package com.example.app


class NativeCode{
    companion object {
        init {
            System.loadLibrary("app")
        }
    }

    external fun getVal(): Int

}