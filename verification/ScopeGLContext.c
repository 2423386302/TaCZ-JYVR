#include <jni.h>
typedef void* E;
extern E eglGetDisplay(void*),eglCreatePbufferSurface(E,E,int*),eglCreateContext(E,E,E,int*);
extern unsigned eglInitialize(E,int*,int*),eglBindAPI(unsigned),eglChooseConfig(E,int*,E*,int,int*),eglMakeCurrent(E,E,E,E);
JNIEXPORT jboolean JNICALL Java_ScopeGLRegression_context(JNIEnv *e,jclass c){
 E d=eglGetDisplay(0),config=0;int n=0,attr[]={0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3038},size[]={0x3057,64,0x3056,64,0x3038},end[]={0x3038};
 if(!eglInitialize(d,0,0)||!eglBindAPI(0x30A2)||!eglChooseConfig(d,attr,&config,1,&n)||!n)return 0;
 E surf=eglCreatePbufferSurface(d,config,size),ctx=eglCreateContext(d,config,0,end);return ctx&&surf&&eglMakeCurrent(d,surf,surf,ctx);
}
