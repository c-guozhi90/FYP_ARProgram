#include <jni.h>
#include "FreetypeJNI.h"
#include <stdio.h>
#include <ft2build.h>
#include FT_FREETYPE_H
#include <android/log.h>

JNIEXPORT jobjectArray JNICALL Java_chenyue_arfyp_common_freetype_FreetypeJNI_getFontTexFromC
        (JNIEnv *env, jclass thisClass, jstring path) {
    const char *TAG = "readFont";
    FT_Library library;
    if (FT_Init_FreeType(&library)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "cannot init library");
        return NULL;
    }
    FT_Face face;
    const char *absPath = env->GetStringUTFChars(path, 0);
    if (FT_New_Face(library, absPath, 0, &face)) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "failed to load font");
        return NULL;
    }
    FT_Set_Pixel_Sizes(face, 300, 0);

    jobjectArray fonts = env->NewObjectArray(128, thisClass, NULL);
    for (int idx = 0; idx < 128; idx++) {
        if (FT_Load_Char(face, idx, FT_LOAD_RENDER)) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "cannont load char bitmap!");
            return NULL;
        }
        jbyte *bufferToJava = (jbyte *) face->glyph->bitmap.buffer;
        jint bitmap_left = face->glyph->bitmap_left;
        jint bitmap_top = face->glyph->bitmap_top;
        jint height = face->glyph->bitmap.rows;
        jint width = face->glyph->bitmap.width;
        jint advance = (face->glyph->advance.x) >> 6;
        jbyteArray bitmapJavaBuffer = env->NewByteArray(width * height);
        env->SetByteArrayRegion(bitmapJavaBuffer, 0, width * height, bufferToJava);
        jmethodID thisConstructorId = env->GetMethodID(thisClass, "<init>", "(IIIII[B)V");
        jobject newFontObj = env->NewObject(thisClass, thisConstructorId, bitmap_left, bitmap_top,
                                            height, width, advance, bitmapJavaBuffer);
        env->SetObjectArrayElement(fonts, idx, newFontObj);
    }

    FT_Done_Face(face);
    FT_Done_FreeType(library);
    return fonts;
}