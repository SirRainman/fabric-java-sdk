package com.rain.fabricdemo.test;

import com.rain.fabricdemo.handler.FirstSampleHandler;
import com.rain.fabricdemo.stream.StreamDataSubscribe;
import com.rain.fabricdemo.handler.DataHandler;

public class App {
    public static void main(String[] args) {

        DataHandler handler = new FirstSampleHandler();
        Object lock = new Object();
        StreamDataSubscribe streamDataSubscribe = new StreamDataSubscribe(handler);
        long beginTime, endTime;

        beginTime = System.currentTimeMillis();
        streamDataSubscribe.start();
        streamDataSubscribe.stop();
        endTime = System.currentTimeMillis();
        System.out.printf("main over: %d ms\n", endTime - beginTime);
    }
}