/*
	Copyright 2017, VIA Technologies, Inc. & OLAMI Team.
	
	http://olami.ai

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package ai.olami.example;

import java.util.Arrays;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import ai.olami.cloudService.APIConfiguration;
import ai.olami.cloudService.APIResponse;
import ai.olami.cloudService.CookieSet;
import ai.olami.cloudService.SpeechRecognizer;
import ai.olami.cloudService.SpeechResult;

public class MicrophoneSpeechRecognizerExample {
	
	// * Replace your APP KEY with this variable.
	private static String mAppKey = "*****your-app-key*****";
	
	// * Replace your APP SECRET with this variable.
	private static String mAppSecret = "*****your-app-secret*****";
	
	// * Replace the localize option you want with this variable.
	// * - Use LOCALIZE_OPTION_SIMPLIFIED_CHINESE for China
	// * - Use LOCALIZE_OPTION_TRADITIONAL_CHINESE for Taiwan 
	private static int mLocalizeOption = APIConfiguration.LOCALIZE_OPTION_SIMPLIFIED_CHINESE;
//	private static int mLocalizeOption = APIConfiguration.LOCALIZE_OPTION_TRADITIONAL_CHINESE;

	private APIConfiguration mAPIConfig = null;
	private SpeechRecognizer mRecoginzer = null;
	private CookieSet mCookie = null;
	
	private ISpeechRecognizerListenerExample mCallback = null;
	
	private boolean mStarted = false;
	private boolean mCancel = false;
	
	private AudioFormat mAudioFormat = null;
	private DataLine.Info mDataLineInfo = null;
	private TargetDataLine mTargetDataLine = null;

	/**
	 * Microphone recorder and the speech recognizer to issue Cloud Speech Recognition API requests.
	 * 
	 * @param listener - Callback to get result and message.
	 */
	public MicrophoneSpeechRecognizerExample(ISpeechRecognizerListenerExample listener) {
		mCallback = listener;
		
		// * Step 1: Configure your key and localize option.
		mAPIConfig = new APIConfiguration(mAppKey, mAppSecret, mLocalizeOption);
		
		// * Step 2: Create the speech recognizer.
		mRecoginzer = new SpeechRecognizer(mAPIConfig);
		
		// * Optional steps: Setup some other configurations.
		mRecoginzer.setEndUserIdentifier("Someone");
		mRecoginzer.setTimeout(10000); 
	}
	
	/**
	 * Setup OLAMI API configurations.
	 * 
	 * @param config - API configurations.
	 */
	public void setAPIConfig(APIConfiguration config) {
		mAPIConfig = config;
	}
	
	/**
	 * @return OLAMI API configurations.
	 */
	public APIConfiguration getAPIConfig() {
		return mAPIConfig;
	}
	
	/**
	 * Start audio recording and the speech recognition.
	 */
	public void start() throws LineUnavailableException, UnsupportedOperationException { 
		if (mTargetDataLine != null) {
			throw new UnsupportedOperationException("Unavailable state.");
		}
		
		mCancel = false;
		mStarted = false;
		
		mRecoginzer.setConfiguration(mAPIConfig);
		mRecoginzer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_RAW);
		// 
		// You can also set the recognizer to use wave format if you are recording with WAVE.
		//
		// For Example :
		// ===================================================================
		// mRecoginzer.setAudioType(SpeechRecognizer.AUDIO_TYPE_PCM_WAVE);
		// ===================================================================
		//
		
		// * Prepare to send audio by a new task identifier.
		mCookie = new CookieSet();
		
		// Create async task to get audio buffer data, then send to the speech recognizer.
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					// Initialize the audio input.
					int channels = 1;
					int frameSize = mRecoginzer.getAudioFrameSize();
					int sampleSize = 16;
					int sampleRate = 16000;
					mAudioFormat = new AudioFormat(sampleRate, sampleSize, channels, true, false);
					mDataLineInfo = new DataLine.Info(TargetDataLine.class, mAudioFormat);
					mTargetDataLine = (TargetDataLine) AudioSystem.getLine(mDataLineInfo);
					mTargetDataLine.open(mAudioFormat);
					mTargetDataLine.start();
					
					// Set the maximum data size to each batch upload. 
					// In this example, we will sending audio data every 0.5 seconds.
					int uploadSize = (500 / SpeechRecognizer.AUDIO_LENGTH_MILLISECONDS_PER_FRAME) * frameSize;
					
					// Set audio buffer size to read.
					int bufferSize = mRecoginzer.getAudioBufferMinSize();
					byte[] buffer = new byte[bufferSize];
					
					// Start reading the recording audio data.
					int nByte = 0;
					while ((!mCancel) && (mTargetDataLine != null) && (nByte != -1)) {
						nByte = mTargetDataLine.read(buffer, 0, bufferSize);
						// * Append audio data to the the buffer of recognizer.
						mRecoginzer.appendAudioFramesData(buffer);
						// * Batch upload.
						if (mRecoginzer.getAppendedAudioSize() >= uploadSize) {
							APIResponse response = mRecoginzer.flushToUploadAudio(mCookie, false);
							if (response.ok()) {
								mStarted = true;
							} else {
								mCallback.onError(response.getErrorMessage());
							}
						}
					}
					
					// Finally upload the remaining appended data, or force to upload an empty data for finish.
					if (mRecoginzer.getAppendedAudioSize() == 0) {
						Arrays.fill(buffer, (byte) 0);
						mRecoginzer.appendAudioFramesData(buffer);
					}
					mRecoginzer.flushToUploadAudio(mCookie, true);
					mStarted = true;
				} catch (Exception ex) {
					cancel();
					mRecoginzer.releaseAppendedAudio();
					mCallback.onError(ex.getMessage());
					throw new RuntimeException(ex);
				}
			}
		}).start();
		
		// Create async task to get speech recognition results from the speech recognizer.
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!mCancel) {
						Thread.sleep(300);
						// Now we can try to get recognition result AFTER the first audio has been uploaded.
						if (mStarted) {
							// * Get recognition result by the task identifier you used for audio upload.
							APIResponse response = mRecoginzer.requestRecognition(mCookie);
							if (response.ok()) {
								if (response.hasData()) {
									SpeechResult sttResult = response.getData().getSpeechResult();
									// Send the speech-to-text result to the callback.
									if (response.getData().getSpeechResult().getStatus() != SpeechResult.STATUS_RESULT_NOT_CHANGE) {
										mCallback.onRecognizeResultChange(sttResult.getResult());
									}
									// Send a message to the callback if the recognition is completed.
									if (sttResult.complete()) {
										mCallback.onRecognizeComplete();
										break;
									}
								}
							} else {
								mCallback.onError(response.getErrorMessage());
							}
						}
					}
				} catch (Exception ex) {
					cancel();
					mCallback.onError(ex.getMessage());
					throw new RuntimeException(ex);
				}
			}
		}).start();
		
	}
	
	/**
	 * Stop audio recording and the speech recognition.
	 */
	public void stop() {
		if (mTargetDataLine != null) {
			mTargetDataLine.stop();
			mTargetDataLine.close();	
			mTargetDataLine = null;
		}
	}
	
	/**
	 * Cancel all tasks.
	 */
	public void cancel() {
		mCancel = true;
		stop();
	}
	
}
