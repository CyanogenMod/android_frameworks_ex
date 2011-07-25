/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <variablespeed.h>

#include <unistd.h>
#include <stdlib.h>

#include <sola_time_scaler.h>
#include <ring_buffer.h>

#include <hlogging.h>

#include <vector>

// ****************************************************************************
// Constants, utility methods, structures and other miscellany used throughout
// this file.

namespace {

// These variables are used to determine the size of the buffer queue used by
// the decoder.
// This is not the same as the large buffer used to hold the uncompressed data
// - for that see the member variable decodeBuffer_.
// The choice of 1152 corresponds to the number of samples per mp3 frame, so is
// a good choice of size for a decoding buffer in the absence of other
// information (we don't know exactly what formats we will be working with).
const size_t kNumberOfBuffersInQueue = 4;
const size_t kNumberOfSamplesPerBuffer = 1152;
const size_t kBufferSizeInBytes = 2 * kNumberOfSamplesPerBuffer;
const size_t kSampleSizeInBytes = 4;

// When calculating play buffer size before pushing to audio player.
const size_t kNumberOfBytesPerInt16 = 2;

// How long to sleep during the main play loop and the decoding callback loop.
// In due course this should be replaced with the better signal and wait on
// condition rather than busy-looping.
const int kSleepTimeMicros = 1000;

// Used in detecting errors with the OpenSL ES framework.
const SLuint32 kPrefetchErrorCandidate =
    SL_PREFETCHEVENT_STATUSCHANGE | SL_PREFETCHEVENT_FILLLEVELCHANGE;

// Structure used when we perform a decoding callback.
typedef struct CallbackContext_ {
    SLMetadataExtractionItf decoderMetadata;
    // Pointer to local storage buffers for decoded audio data.
    int8_t* pDataBase;
    // Pointer to the current buffer within local storage.
    int8_t* pData;
} CallbackContext;

// Local storage for decoded audio data.
int8_t pcmData[kNumberOfBuffersInQueue * kBufferSizeInBytes];

#define CheckSLResult(message, result) \
    CheckSLResult_Real(message, result, __LINE__)

// Helper function for debugging - checks the OpenSL result for success.
void CheckSLResult_Real(const char* message, SLresult result, int line) {
  // This can be helpful when debugging.
  // LOGD("sl result %d for %s", result, message);
  if (SL_RESULT_SUCCESS != result) {
    LOGE("slresult was %d at %s file variablespeed line %d",
        static_cast<int>(result), message, line);
  }
  CHECK(SL_RESULT_SUCCESS == result);
}

}  // namespace

// ****************************************************************************
// Static instance of audio engine, and methods for getting, setting and
// deleting it.

// The single global audio engine instance.
AudioEngine* AudioEngine::audioEngine_ = NULL;
android::Mutex publishEngineLock_;

AudioEngine* AudioEngine::GetEngine() {
  android::Mutex::Autolock autoLock(publishEngineLock_);
  if (audioEngine_ == NULL) {
    LOGE("you haven't initialized the audio engine");
    CHECK(false);
    return NULL;
  }
  return audioEngine_;
}

void AudioEngine::SetEngine(AudioEngine* engine) {
  if (audioEngine_ != NULL) {
    LOGE("you have already set the audio engine");
    CHECK(false);
    return;
  }
  audioEngine_ = engine;
}

void AudioEngine::DeleteEngine() {
  if (audioEngine_ == NULL) {
    LOGE("you haven't initialized the audio engine");
    CHECK(false);
    return;
  }
  delete audioEngine_;
  audioEngine_ = NULL;
}

// ****************************************************************************
// The callbacks from the engine require static callback functions.
// Here are the static functions - they just delegate to instance methods on
// the engine.

static void PlayingBufferQueueCb(SLAndroidSimpleBufferQueueItf, void*) {
  AudioEngine::GetEngine()->PlayingBufferQueueCallback();
}

static void PrefetchEventCb(SLPrefetchStatusItf caller, void*, SLuint32 event) {
  AudioEngine::GetEngine()->PrefetchEventCallback(caller, event);
}

static void DecodingBufferQueueCb(SLAndroidSimpleBufferQueueItf queueItf,
    void *context) {
  AudioEngine::GetEngine()->DecodingBufferQueueCallback(queueItf, context);
}

static void DecodingEventCb(SLPlayItf caller, void*, SLuint32 event) {
  AudioEngine::GetEngine()->DecodingEventCallback(caller, event);
}

// ****************************************************************************
// Static utility methods.

static void PausePlaying(SLPlayItf playItf) {
  SLresult result = (*playItf)->SetPlayState(playItf, SL_PLAYSTATE_PAUSED);
  CheckSLResult("pause playing", result);
}

static void StartPlaying(SLPlayItf playItf) {
  SLresult result = (*playItf)->SetPlayState(playItf, SL_PLAYSTATE_PLAYING);
  CheckSLResult("start playing", result);
}

static void StopPlaying(SLPlayItf playItf) {
  SLresult result = (*playItf)->SetPlayState(playItf, SL_PLAYSTATE_STOPPED);
  CheckSLResult("stop playing", result);
}

static void ExtractMetadataFromDecoder(
    SLMetadataExtractionItf decoderMetadata) {
  SLuint32 itemCount;
  SLresult result = (*decoderMetadata)->GetItemCount(
      decoderMetadata, &itemCount);
  CheckSLResult("getting item count", result);
  SLuint32 i, keySize, valueSize;
  SLMetadataInfo *keyInfo, *value;
  for (i = 0; i < itemCount ; ++i) {
    keyInfo = NULL;
    keySize = 0;
    value = NULL;
    valueSize = 0;
    result = (*decoderMetadata)->GetKeySize(decoderMetadata, i, &keySize);
    CheckSLResult("get key size", result);
    keyInfo = static_cast<SLMetadataInfo*>(malloc(keySize));
    if (keyInfo) {
      result = (*decoderMetadata)->GetKey(
          decoderMetadata, i, keySize, keyInfo);
      CheckSLResult("get key", result);
      if (keyInfo->encoding == SL_CHARACTERENCODING_ASCII
          || keyInfo->encoding == SL_CHARACTERENCODING_UTF8) {
        result = (*decoderMetadata)->GetValueSize(
            decoderMetadata, i, &valueSize);
        CheckSLResult("get value size", result);
        value = static_cast<SLMetadataInfo*>(malloc(valueSize));
        if (value) {
          result = (*decoderMetadata)->GetValue(
              decoderMetadata, i, valueSize, value);
          CheckSLResult("get value", result);
          if (value->encoding == SL_CHARACTERENCODING_BINARY) {
            LOGD("key[%d] size=%d, name=%s value size=%d value=%d",
                i, keyInfo->size, keyInfo->data, value->size,
                *(reinterpret_cast<SLuint32*>(value->data)));
          }
          free(value);
        }
      }
      free(keyInfo);
    }
  }
}

static void SeekToPosition(SLSeekItf seekItf, size_t startPositionMillis) {
  SLresult result = (*seekItf)->SetPosition(
      seekItf, startPositionMillis, SL_SEEKMODE_ACCURATE);
  CheckSLResult("seek to position", result);
}

static void RegisterCallbackContextAndAddEnqueueBuffersToDecoder(
    SLAndroidSimpleBufferQueueItf decoderQueue,
    SLMetadataExtractionItf decoderMetadata, android::Mutex &callbackLock,
    CallbackContext* context) {
  android::Mutex::Autolock autoLock(callbackLock);
  // Initialize the callback structure, used during the decoding.
  // Then register a callback on the decoder queue, so that we will be called
  // throughout the decoding process (and can then extract the decoded audio
  // for the next bit of the pipeline).
  context->decoderMetadata = decoderMetadata;
  context->pDataBase = pcmData;
  context->pData = pcmData;

  SLresult result = (*decoderQueue)->RegisterCallback(
      decoderQueue, DecodingBufferQueueCb, context);
  CheckSLResult("decode callback", result);

  // Enqueue buffers to map the region of memory allocated to store the
  // decoded data.
  for (size_t i = 0; i < kNumberOfBuffersInQueue; i++) {
    SLresult result = (*decoderQueue)->Enqueue(
        decoderQueue, context->pData, kBufferSizeInBytes);
    CheckSLResult("enqueue something", result);
    context->pData += kBufferSizeInBytes;
  }
  context->pData = context->pDataBase;
}

// ****************************************************************************
// Constructor and Destructor.

AudioEngine::AudioEngine(size_t channels, size_t sampleRate,
    size_t targetFrames, float windowDuration, float windowOverlapDuration,
    size_t maxPlayBufferCount, float initialRate, size_t decodeInitialSize,
    size_t decodeMaxSize, size_t startPositionMillis)
    : decodeBuffer_(decodeInitialSize, decodeMaxSize),
      playingBuffers_(), freeBuffers_(), timeScaler_(NULL),
      floatBuffer_(NULL), injectBuffer_(NULL),
      channels_(channels), sampleRate_(sampleRate),
      targetFrames_(targetFrames),
      windowDuration_(windowDuration),
      windowOverlapDuration_(windowOverlapDuration),
      maxPlayBufferCount_(maxPlayBufferCount), initialRate_(initialRate),
      startPositionMillis_(startPositionMillis),
      totalDurationMs_(0), startRequested_(false),
      stopRequested_(false), finishedDecoding_(false) {
  floatBuffer_ = new float[targetFrames_ * channels_];
  injectBuffer_ = new float[targetFrames_ * channels_];
}

AudioEngine::~AudioEngine() {
  // destroy the time scaler
  if (timeScaler_ != NULL) {
    delete timeScaler_;
    timeScaler_ = NULL;
  }

  // delete all outstanding playing and free buffers
  android::Mutex::Autolock autoLock(playBufferLock_);
  while (playingBuffers_.size() > 0) {
    delete[] playingBuffers_.front();
    playingBuffers_.pop();
  }
  while (freeBuffers_.size() > 0) {
    delete[] freeBuffers_.top();
    freeBuffers_.pop();
  }

  delete[] floatBuffer_;
  floatBuffer_ = NULL;
  delete[] injectBuffer_;
  injectBuffer_ = NULL;
}

// ****************************************************************************
// Regular AudioEngine class methods.

void AudioEngine::SetVariableSpeed(float speed) {
  GetTimeScaler()->set_speed(speed);
}

void AudioEngine::RequestStart() {
  android::Mutex::Autolock autoLock(lock_);
  startRequested_ = true;
}

void AudioEngine::ClearRequestStart() {
  android::Mutex::Autolock autoLock(lock_);
  startRequested_ = false;
}

bool AudioEngine::GetWasStartRequested() {
  android::Mutex::Autolock autoLock(lock_);
  return startRequested_;
}

void AudioEngine::RequestStop() {
  android::Mutex::Autolock autoLock(lock_);
  stopRequested_ = true;
}

int AudioEngine::GetCurrentPosition() {
  android::Mutex::Autolock autoLock(decodeBufferLock_);
  double result = decodeBuffer_.GetTotalAdvancedCount();
  return static_cast<int>(
      (result * 1000) / sampleRate_ / channels_ + startPositionMillis_);
}

int AudioEngine::GetTotalDuration() {
  android::Mutex::Autolock autoLock(lock_);
  return static_cast<int>(totalDurationMs_);
}

video_editing::SolaTimeScaler* AudioEngine::GetTimeScaler() {
  if (timeScaler_ == NULL) {
    timeScaler_ = new video_editing::SolaTimeScaler();
    timeScaler_->Init(sampleRate_, channels_, initialRate_, windowDuration_,
        windowOverlapDuration_);
  }
  return timeScaler_;
}

void AudioEngine::PrefetchDurationSampleRateAndChannels(
    SLPlayItf playItf, SLPrefetchStatusItf prefetchItf) {
  // Set play state to pause, to begin the prefetching.
  PausePlaying(playItf);

  // Wait until the data has been prefetched.
  {
    SLuint32 prefetchStatus = SL_PREFETCHSTATUS_UNDERFLOW;
    android::Mutex::Autolock autoLock(prefetchLock_);
    while (prefetchStatus != SL_PREFETCHSTATUS_SUFFICIENTDATA) {
      LOGI("waiting for condition");
      // prefetchCondition_.waitRelative(prefetchLock, 1000 * 1000 * 10);
      usleep(10 * 1000);
      LOGI("getting the value");
      (*prefetchItf)->GetPrefetchStatus(prefetchItf, &prefetchStatus);
    }
    LOGI("done with wait");
  }

  SLmillisecond durationInMsec = SL_TIME_UNKNOWN;
  SLresult result = (*playItf)->GetDuration(playItf, &durationInMsec);
  CheckSLResult("getting duration", result);
  CHECK(durationInMsec != SL_TIME_UNKNOWN);
  LOGD("duration: %d", static_cast<int>(durationInMsec));
  android::Mutex::Autolock autoLock(lock_);
  totalDurationMs_ = durationInMsec;
}

bool AudioEngine::EnqueueNextBufferOfAudio(
    SLAndroidSimpleBufferQueueItf audioPlayerQueue) {
  size_t frameSizeInBytes = kSampleSizeInBytes * channels_;
  size_t frameCount = 0;
  while (frameCount < targetFrames_) {
    size_t framesLeft = targetFrames_ - frameCount;
    // If there is data already in the time scaler, retrieve it.
    if (GetTimeScaler()->available() > 0) {
      size_t retrieveCount = min(GetTimeScaler()->available(), framesLeft);
      int count = GetTimeScaler()->RetrieveSamples(
          floatBuffer_ + frameCount * channels_, retrieveCount);
      if (count <= 0) {
        LOGD("ERROR: Count was %d", count);
        break;
      }
      frameCount += count;
      continue;
    }
    // If there is no data in the time scaler, then feed some into it.
    android::Mutex::Autolock autoLock(decodeBufferLock_);
    size_t framesInDecodeBuffer =
        decodeBuffer_.GetSizeInBytes() / frameSizeInBytes;
    size_t framesScalerCanHandle = GetTimeScaler()->input_limit();
    size_t framesToInject = min(framesInDecodeBuffer,
        min(targetFrames_, framesScalerCanHandle));
    if (framesToInject <= 0) {
      // No more frames left to inject.
      break;
    }
    for (size_t i = 0; i < framesToInject * channels_ ; ++i) {
      injectBuffer_[i] = decodeBuffer_.GetAtIndex(i);
    }
    int count = GetTimeScaler()->InjectSamples(injectBuffer_, framesToInject);
    if (count <= 0) {
      LOGD("ERROR: Count was %d", count);
      break;
    }
    decodeBuffer_.AdvanceHeadPointerShorts(count * channels_);
  }
  if (frameCount <= 0) {
    // We must have finished playback.
    if (GetEndOfDecoderReached()) {
      // If we've finished decoding, clear the buffer - so we will terminate.
      ClearDecodeBuffer();
    }
    return false;
  }

  // Get a free playing buffer.
  int16* playBuffer;
  {
    android::Mutex::Autolock autoLock(playBufferLock_);
    if (freeBuffers_.size() > 0) {
      // If we have a free buffer, recycle it.
      playBuffer = freeBuffers_.top();
      freeBuffers_.pop();
    } else {
      // Otherwise allocate a new one.
      playBuffer = new int16[targetFrames_ * channels_];
    }
  }

  // Try to play the buffer.
  for (size_t i = 0; i < frameCount * channels_ ; ++i) {
    playBuffer[i] = floatBuffer_[i];
  }
  size_t sizeOfPlayBufferInBytes =
      frameCount * channels_ * kNumberOfBytesPerInt16;
  SLresult result = (*audioPlayerQueue)->Enqueue(audioPlayerQueue, playBuffer,
      sizeOfPlayBufferInBytes);
  CheckSLResult("enqueue prebuilt audio", result);
  if (result == SL_RESULT_SUCCESS) {
    android::Mutex::Autolock autoLock(playBufferLock_);
    playingBuffers_.push(playBuffer);
  } else {
    LOGE("could not enqueue audio buffer");
    delete[] playBuffer;
  }

  return (result == SL_RESULT_SUCCESS);
}

bool AudioEngine::GetEndOfDecoderReached() {
  android::Mutex::Autolock autoLock(lock_);
  return finishedDecoding_;
}

void AudioEngine::SetEndOfDecoderReached() {
  android::Mutex::Autolock autoLock(lock_);
  finishedDecoding_ = true;
}

bool AudioEngine::PlayFileDescriptor(int fd, int64 offset, int64 length) {
  SLDataLocator_AndroidFD loc_fd = {
      SL_DATALOCATOR_ANDROIDFD, fd, offset, length };
  SLDataFormat_MIME format_mime = {
      SL_DATAFORMAT_MIME, NULL, SL_CONTAINERTYPE_UNSPECIFIED };
  SLDataSource audioSrc = { &loc_fd, &format_mime };
  return PlayFromThisSource(audioSrc);
}

bool AudioEngine::PlayUri(const char* uri) {
  // Source of audio data for the decoding
  SLDataLocator_URI decUri = { SL_DATALOCATOR_URI,
      const_cast<SLchar*>(reinterpret_cast<const SLchar*>(uri)) };
  SLDataFormat_MIME decMime = {
      SL_DATAFORMAT_MIME, NULL, SL_CONTAINERTYPE_UNSPECIFIED };
  SLDataSource decSource = { &decUri, &decMime };
  return PlayFromThisSource(decSource);
}

bool AudioEngine::IsDecodeBufferEmpty() {
  android::Mutex::Autolock autoLock(decodeBufferLock_);
  return decodeBuffer_.GetSizeInBytes() <= 0;
}

void AudioEngine::ClearDecodeBuffer() {
  android::Mutex::Autolock autoLock(decodeBufferLock_);
  decodeBuffer_.Clear();
}

static void CreateAndRealizeEngine(SLObjectItf &engine,
    SLEngineItf &engineInterface) {
  SLEngineOption EngineOption[] = { {
      SL_ENGINEOPTION_THREADSAFE, SL_BOOLEAN_TRUE } };
  SLresult result = slCreateEngine(&engine, 1, EngineOption, 0, NULL, NULL);
  CheckSLResult("create engine", result);
  result = (*engine)->Realize(engine, SL_BOOLEAN_FALSE);
  CheckSLResult("realise engine", result);
  result = (*engine)->GetInterface(engine, SL_IID_ENGINE, &engineInterface);
  CheckSLResult("get interface", result);
}

static void CreateAndRealizeOutputMix(SLEngineItf &engineInterface,
    SLObjectItf &outputMix) {
  SLresult result;
  // Create the output mix for playing.
  result = (*engineInterface)->CreateOutputMix(
      engineInterface, &outputMix, 0, NULL, NULL);
  CheckSLResult("create output mix", result);
  result = (*outputMix)->Realize(outputMix, SL_BOOLEAN_FALSE);
  CheckSLResult("realize", result);
}

static void CreateAndRealizeAudioPlayer(size_t sampleRate, size_t channels,
    SLObjectItf &outputMix, SLObjectItf &audioPlayer,
    SLEngineItf &engineInterface) {
  SLresult result;
  SLuint32 slSampleRate;
  SLuint32 slOutputChannels;
  switch (sampleRate) {
    case 44100:
      slSampleRate = SL_SAMPLINGRATE_44_1;
      break;
    case 8000:
      slSampleRate = SL_SAMPLINGRATE_8;
      break;
    case 11025:
      slSampleRate = SL_SAMPLINGRATE_11_025;
      break;
    default:
      LOGE("unknown sample rate, using SL_SAMPLINGRATE_44_1");
      slSampleRate = SL_SAMPLINGRATE_44_1;
      break;
  }
  switch (channels) {
    case 2:
      slOutputChannels = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
      break;
    case 1:
      slOutputChannels = SL_SPEAKER_FRONT_LEFT;
      break;
    default:
      LOGE("unknown channels, using 2");
      slOutputChannels = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
      break;
  }

  // Define the source and sink for the audio player: comes from a buffer queue
  // and goes to the output mix.
  SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {
      SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2 };
  SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, channels, slSampleRate,
      SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
      slOutputChannels, SL_BYTEORDER_LITTLEENDIAN};
  SLDataSource playingSrc = {&loc_bufq, &format_pcm};
  SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMix};
  SLDataSink audioSnk = {&loc_outmix, NULL};

  // Create the audio player, which will play from the buffer queue and send to
  // the output mix.
  const size_t playerInterfaceCount = 1;
  const SLInterfaceID iids[playerInterfaceCount] = {
      SL_IID_ANDROIDSIMPLEBUFFERQUEUE };
  const SLboolean reqs[playerInterfaceCount] = { SL_BOOLEAN_TRUE };
  result = (*engineInterface)->CreateAudioPlayer(engineInterface, &audioPlayer,
      &playingSrc, &audioSnk, playerInterfaceCount, iids, reqs);
  CheckSLResult("create audio player", result);
  result = (*audioPlayer)->Realize(audioPlayer, SL_BOOLEAN_FALSE);
  CheckSLResult("realize buffer queue", result);
}

static void GetAudioPlayInterfacesAndRegisterCallback(SLObjectItf &audioPlayer,
    SLPlayItf &audioPlayerPlay,
    SLAndroidSimpleBufferQueueItf &audioPlayerQueue) {
  SLresult result;
  // Get the play interface from the player, as well as the buffer queue
  // interface from its source.
  // Register for callbacks during play.
  result = (*audioPlayer)->GetInterface(
      audioPlayer, SL_IID_PLAY, &audioPlayerPlay);
  CheckSLResult("get interface", result);
  result = (*audioPlayer)->GetInterface(audioPlayer,
      SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &audioPlayerQueue);
  CheckSLResult("get interface again", result);
  result = (*audioPlayerQueue)->RegisterCallback(
      audioPlayerQueue, PlayingBufferQueueCb, NULL);
  CheckSLResult("register callback", result);
}

bool AudioEngine::PlayFromThisSource(const SLDataSource& audioSrc) {
  ClearDecodeBuffer();

  SLresult result;

  SLObjectItf engine;
  SLEngineItf engineInterface;
  CreateAndRealizeEngine(engine, engineInterface);

  // Define the source and sink for the decoding player: comes from the source
  // this method was called with, is sent to another buffer queue.
  SLDataLocator_AndroidSimpleBufferQueue decBuffQueue;
  decBuffQueue.locatorType = SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE;
  decBuffQueue.numBuffers = kNumberOfBuffersInQueue;
  // A valid value seems required here but is currently ignored.
  SLDataFormat_PCM pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_44_1,
      SL_PCMSAMPLEFORMAT_FIXED_16, 16,
      SL_SPEAKER_FRONT_LEFT, SL_BYTEORDER_LITTLEENDIAN};
  SLDataSink decDest = { &decBuffQueue, &pcm };

  // Create the decoder with the given source and sink.
  const size_t decoderInterfaceCount = 4;
  SLObjectItf decoder;
  const SLInterfaceID decodePlayerInterfaces[decoderInterfaceCount] = {
      SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_PREFETCHSTATUS, SL_IID_SEEK,
      SL_IID_METADATAEXTRACTION };
  const SLboolean decodePlayerRequired[decoderInterfaceCount] = {
      SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE };
  SLDataSource sourceCopy(audioSrc);
  result = (*engineInterface)->CreateAudioPlayer(engineInterface, &decoder,
      &sourceCopy, &decDest, decoderInterfaceCount, decodePlayerInterfaces,
      decodePlayerRequired);
  CheckSLResult("create audio player", result);
  result = (*decoder)->Realize(decoder, SL_BOOLEAN_FALSE);
  CheckSLResult("realize in sync mode", result);

  // Get the play interface from the decoder, and register event callbacks.
  // Get the buffer queue, prefetch and seek interfaces.
  SLPlayItf decoderPlay = NULL;
  result = (*decoder)->GetInterface(decoder, SL_IID_PLAY, &decoderPlay);
  CheckSLResult("get play interface, implicit", result);
  result = (*decoderPlay)->SetCallbackEventsMask(
      decoderPlay, SL_PLAYEVENT_HEADATEND);
  CheckSLResult("set the event mask", result);
  result = (*decoderPlay)->RegisterCallback(
      decoderPlay, DecodingEventCb, NULL);
  CheckSLResult("register decoding event callback", result);
  SLAndroidSimpleBufferQueueItf decoderQueue;
  result = (*decoder)->GetInterface(
      decoder, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &decoderQueue);
  CheckSLResult("get decoder buffer queue", result);
  SLPrefetchStatusItf decoderPrefetch;
  result = (*decoder)->GetInterface(
      decoder, SL_IID_PREFETCHSTATUS, &decoderPrefetch);
  CheckSLResult("get prefetch status interface", result);
  SLSeekItf decoderSeek;
  result = (*decoder)->GetInterface(decoder, SL_IID_SEEK, &decoderSeek);
  CheckSLResult("get seek interface", result);

  // Get the metadata interface from the decoder.
  SLMetadataExtractionItf decoderMetadata;
  result = (*decoder)->GetInterface(decoder,
      SL_IID_METADATAEXTRACTION, &decoderMetadata);
  CheckSLResult("getting metadata interface", result);

  CallbackContext callbackContext;
  RegisterCallbackContextAndAddEnqueueBuffersToDecoder(
      decoderQueue, decoderMetadata, callbackLock_, &callbackContext);

  // Initialize the callback for prefetch errors, if we can't open the
  // resource to decode.
  result = (*decoderPrefetch)->SetCallbackEventsMask(
      decoderPrefetch, kPrefetchErrorCandidate);
  CheckSLResult("set prefetch callback mask", result);
  result = (*decoderPrefetch)->RegisterCallback(
      decoderPrefetch, PrefetchEventCb, &decoderPrefetch);
  CheckSLResult("set prefetch callback", result);

  SeekToPosition(decoderSeek, startPositionMillis_);

  PrefetchDurationSampleRateAndChannels(decoderPlay, decoderPrefetch);

  StartPlaying(decoderPlay);

  SLObjectItf outputMix = NULL;
  SLObjectItf audioPlayer = NULL;
  SLPlayItf audioPlayerPlay = NULL;
  SLAndroidSimpleBufferQueueItf audioPlayerQueue = NULL;

  // The main loop - until we're told to stop: if there is audio data coming
  // out of the decoder, feed it through the time scaler.
  // As it comes out of the time scaler, feed it into the audio player.
  while (!Finished()) {
    if (GetWasStartRequested()) {
      CreateAndRealizeOutputMix(engineInterface, outputMix);
      CreateAndRealizeAudioPlayer(sampleRate_, channels_, outputMix,
          audioPlayer, engineInterface);
      GetAudioPlayInterfacesAndRegisterCallback(audioPlayer, audioPlayerPlay,
          audioPlayerQueue);
      ClearRequestStart();
      StartPlaying(audioPlayerPlay);
    }
    EnqueueMoreAudioIfNecessary(audioPlayerQueue);
    usleep(kSleepTimeMicros);
  }

  // Delete the audio player and output mix, iff they have been created.
  if (audioPlayer != NULL) {
    StopPlaying(audioPlayerPlay);
    result = (*audioPlayerQueue)->Clear(audioPlayerQueue);
    CheckSLResult("clear audio player queue", result);
    result = (*audioPlayerQueue)->RegisterCallback(audioPlayerQueue, NULL, NULL);
    CheckSLResult("clear callback", result);
    (*audioPlayer)->AbortAsyncOperation(audioPlayer);
    (*audioPlayer)->Destroy(audioPlayer);
    (*outputMix)->Destroy(outputMix);
    audioPlayer = NULL;
    audioPlayerPlay = NULL;
    audioPlayerQueue = NULL;
    outputMix = NULL;
  }

  // Delete the decoder.
  StopPlaying(decoderPlay);
  result = (*decoderPrefetch)->RegisterCallback(decoderPrefetch, NULL, NULL);
  CheckSLResult("clearing prefetch error callback", result);
  // This is returning slresult 13 if I do no playback.
  // Repro is to comment out all before this line, and all after enqueueing
  // my buffers.
  // result = (*decoderQueue)->Clear(decoderQueue);
  // CheckSLResult("clearing decode buffer queue", result);
  result = (*decoderQueue)->RegisterCallback(decoderQueue, NULL, NULL);
  CheckSLResult("clearing decode callback", result);
  decoderSeek = NULL;
  decoderPrefetch = NULL;
  decoderQueue = NULL;
  result = (*decoderPlay)->RegisterCallback(decoderPlay, NULL, NULL);
  CheckSLResult("clear decoding event callback", result);
  (*decoder)->AbortAsyncOperation(decoder);
  decoderPlay = NULL;
  (*decoder)->Destroy(decoder);

  // Delete the engine.
  (*engine)->Destroy(engine);
  engineInterface = NULL;

  return true;
}

bool AudioEngine::Finished() {
  if (GetWasStopRequested()) {
    return true;
  }
  android::Mutex::Autolock autoLock(playBufferLock_);
  return playingBuffers_.size() <= 0 &&
      IsDecodeBufferEmpty() &&
      GetEndOfDecoderReached();
}

bool AudioEngine::GetWasStopRequested() {
  android::Mutex::Autolock autoLock(lock_);
  return stopRequested_;
}

bool AudioEngine::GetHasReachedPlayingBuffersLimit() {
  android::Mutex::Autolock autoLock(playBufferLock_);
  return playingBuffers_.size() >= maxPlayBufferCount_;
}

void AudioEngine::EnqueueMoreAudioIfNecessary(
    SLAndroidSimpleBufferQueueItf audioPlayerQueue) {
  bool keepEnqueueing = true;
  while (!GetWasStopRequested() &&
         !IsDecodeBufferEmpty() &&
         !GetHasReachedPlayingBuffersLimit() &&
         keepEnqueueing) {
    keepEnqueueing = EnqueueNextBufferOfAudio(audioPlayerQueue);
  }
}

bool AudioEngine::DecodeBufferTooFull() {
  android::Mutex::Autolock autoLock(decodeBufferLock_);
  return decodeBuffer_.IsTooLarge();
}

// ****************************************************************************
// Code for handling the static callbacks.

void AudioEngine::PlayingBufferQueueCallback() {
  // The head playing buffer is done, move it to the free list.
  android::Mutex::Autolock autoLock(playBufferLock_);
  if (playingBuffers_.size() > 0) {
    freeBuffers_.push(playingBuffers_.front());
    playingBuffers_.pop();
  }
}

void AudioEngine::PrefetchEventCallback(
    SLPrefetchStatusItf caller, SLuint32 event) {
  // If there was a problem during decoding, then signal the end.
  SLpermille level = 0;
  SLresult result = (*caller)->GetFillLevel(caller, &level);
  CheckSLResult("get fill level", result);
  SLuint32 status;
  result = (*caller)->GetPrefetchStatus(caller, &status);
  CheckSLResult("get prefetch status", result);
  if ((kPrefetchErrorCandidate == (event & kPrefetchErrorCandidate)) &&
      (level == 0) &&
      (status == SL_PREFETCHSTATUS_UNDERFLOW)) {
    LOGI("PrefetchEventCallback error while prefetching data");
    SetEndOfDecoderReached();
  }
  if (SL_PREFETCHSTATUS_SUFFICIENTDATA == event) {
    // android::Mutex::Autolock autoLock(prefetchLock_);
    // prefetchCondition_.broadcast();
  }
}

void AudioEngine::DecodingBufferQueueCallback(
    SLAndroidSimpleBufferQueueItf queueItf, void *context) {
  if (GetWasStopRequested()) {
    return;
  }

  CallbackContext *pCntxt;
  {
    android::Mutex::Autolock autoLock(callbackLock_);
    pCntxt = reinterpret_cast<CallbackContext*>(context);
  }
  {
    android::Mutex::Autolock autoLock(decodeBufferLock_);
    decodeBuffer_.AddData(pCntxt->pDataBase, kBufferSizeInBytes);
  }

  // TODO: This call must be added back in to fix the bug relating to using
  // the correct sample rate and channels.  I will do this in the follow-up.
  // ExtractMetadataFromDecoder(pCntxt->decoderMetadata);

  // Increase data pointer by buffer size
  pCntxt->pData += kBufferSizeInBytes;
  if (pCntxt->pData >= pCntxt->pDataBase +
      (kNumberOfBuffersInQueue * kBufferSizeInBytes)) {
    pCntxt->pData = pCntxt->pDataBase;
  }

  SLresult result = (*queueItf)->Enqueue(
      queueItf, pCntxt->pDataBase, kBufferSizeInBytes);
  CheckSLResult("enqueue something else", result);

  // If we get too much data into the decoder,
  // sleep until the playback catches up.
  while (!GetWasStopRequested() && DecodeBufferTooFull()) {
    usleep(kSleepTimeMicros);
  }
}

void AudioEngine::DecodingEventCallback(SLPlayItf, SLuint32 event) {
  if (SL_PLAYEVENT_HEADATEND & event) {
    SetEndOfDecoderReached();
  }
}
