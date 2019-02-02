#include <jni.h>
#include "FreetypeJNI.h"
#include <stdio.h>
#include <ft2build.h>
#include FT_FREETYPE_H

JNIEXPORT jobjectArray JNICALL Java_chenyue_arfyp_common_freetype_FreetypeJNI_getFontTexFromC
        (JNIEnv *env, jclass thisClass, jstring path) {
    FT_Library library;
    if (FT_Init_FreeType(&library)) {
        printf("cannot init library");
        return NULL;
    }
    FT_Face face;
    const char *absPath = env->GetStringUTFChars(path, 0);
    if (FT_New_Face(library, absPath, 0, &face)) {
        printf("failed to load font");
        return NULL;
    }
    FT_Set_Pixel_Sizes(face, 100, 0);
    if (FT_Load_Char(face, 'a', FT_LOAD_RENDER)) {
        printf("cannont load char bitmap!");
        return NULL;
    }
    jbyte *bufferToJava = (jbyte *) face->glyph->bitmap.buffer;
    jint bitmap_left = face->glyph->bitmap_left;
    jint bitmap_top = face->glyph->bitmap_top;
    jint height = face->glyph->bitmap.rows;
    jint width = face->glyph->bitmap.width;
    jbyteArray bitmapJavaBuffer = env->NewByteArray(width * height);
    env->SetByteArrayRegion(bitmapJavaBuffer, 0, width * height, bufferToJava);
    jmethodID thisConstructorId = env->GetMethodID(thisClass, "<init>", "(IIII[B)V");
    jobject newFontObj = env->NewObject(thisClass, thisConstructorId, bitmap_left, bitmap_top,
                                        height, width, bitmapJavaBuffer);
    jobjectArray fonts = env->NewObjectArray(1, thisClass, NULL);
    env->SetObjectArrayElement(fonts, 0, newFontObj);
    return fonts;
}