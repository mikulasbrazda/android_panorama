// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("myapplication");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//      }//         System.loadLibrary("myapplication")
//    }

#include <jni.h>
#include <string>
#include "opencv2/stitching.hpp"
#include "opencv2/imgcodecs.hpp"
#include "opencv2/highgui.hpp"
#include <iostream>
#include <opencv2/opencv.hpp>

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_myapplication_PanoramaActivity_stitchImages(JNIEnv *env, jobject clazz, jlongArray imageAddressArray, jlong outputAddress){
        using namespace cv;
        using namespace std;
        jint ret=1;
        // Get the length of the long array
        jsize a_len = env->GetArrayLength(imageAddressArray);
        // Convert the jlongArray to an array of jlong
        jlong *imgAddressArr = env->GetLongArrayElements(imageAddressArray, 0);
        // Create a vector to store all the image
        if (imgAddressArr == NULL) {
            return ret; /* exception occurred */
        }
        if (a_len <= 0) {
            return ret; /* exception occurred */
        }
        if (outputAddress == 0) {
            return ret; /* exception occurred */
        }
        for (int i = 0; i < a_len; i++) {
            if (imgAddressArr[i] == 0) {
                return i; /* exception occurred */
            }
        }


        vector<Mat> imgVec;

        for(int k=0;k<a_len;k++){
            // Get the image
            Mat & curimage=*(Mat*)imgAddressArr[k];
            Mat newimage;

            // Convert to a 3 channel Mat to use with Stitcher module
            cvtColor(curimage, newimage, cv::COLOR_RGBA2RGB);

            // Reduce the resolution for fast computation
            float scale = 1000.0f / curimage.rows;
            resize(newimage, newimage, Size(scale * curimage.rows, scale * curimage.cols));
            // resize(newimage, newimage, Size(800,600));

            imgVec.push_back(newimage);
        }
        std::cout << "a_len: " << a_len << "\n";

        Mat & result  = *(Mat*) outputAddress;

        Ptr<Stitcher> stitcher = Stitcher::create(Stitcher::PANORAMA);
        if(stitcher == NULL){
            return 5;
        }
        // hconcat images
       //hconcat(imgVec, result);
        //ret = 0;
       Stitcher::Status status = stitcher->stitch(imgVec, result);

       if (status == Stitcher::OK){
           ret=0;
        }
        // Release the jlong array
        env->ReleaseLongArrayElements(imageAddressArray, imgAddressArr ,0);
        return ret;

    }
