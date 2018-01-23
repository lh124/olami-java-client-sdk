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

public interface ISpeechRecognizerListenerExample {
	
 /**
  * Callback when the recognize process state changes.
  */
 void onRecognizeComplete();

 /**
  * Callback when the results of speech recognition changes.
  *
  * @param recognitionTextResult - Speech-to-Text result.
  */
 void onRecognizeResultChange(String recognitionTextResult);
 
 /**
  * Callback when the status of speech recognition changes.
  *
  * @param statusMessage - Status Message.
  */
 void onRecognizeStatusChange(String statusMessage);
 
 /**
  * Callback when error occurs.
  *
  * @param errorMessage - Error Message.
  */
 void onError(String errorMessage);
 
}
