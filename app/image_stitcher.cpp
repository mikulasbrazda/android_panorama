//
// Created by Miky on 24.02.2024.
//
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <vector>

using namespace cv;
using namespace std;

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_myapplication_MainActivity_stitchImages(JNIEnv *env, jobject, jlongArray imageAddresses) {
    // Convert jlongArray to std::vector<cv::Mat>
    jsize a_len = env->GetArrayLength(imageAddresses);
    jlong *imgAddrs = env->GetLongArrayElements(imageAddresses, 0);
    vector<Mat> images;
    for (int i = 0; i < a_len; i++) {
        Mat &img = *(Mat *)imgAddrs[i];
        images.push_back(img);
    }

    // Use OpenCV Stitcher
    Mat pano;
    Ptr<Stitcher> stitcher = Stitcher::create(Stitcher::PANORAMA);
    Stitcher::Status status = stitcher->stitch(images, pano);

    if (status != Stitcher::OK) {
        // Handle stitching error
        return 0;
    }

    // Return the address of the panorama Mat object
    Mat *panoAddr = new Mat(pano);
    return (jlong)panoAddr;
}
