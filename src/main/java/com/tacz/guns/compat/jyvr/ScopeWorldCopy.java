package com.tacz.guns.compat.jyvr;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
/** Private, single-sample color target. Never read from the target while drawing into it. */
public final class ScopeWorldCopy {
    private int texture=-1, framebuffer, width,height;
    public int texture(){return texture;}
    public boolean capture(int source,int attachment,int x,int y,int w,int h){
        if(source==framebuffer&&framebuffer!=0||w<1||h<1||attachment==GL_NONE)return false;
        int read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int bound=glGetInteger(GL_TEXTURE_BINDING_2D);boolean scissor=glIsEnabled(GL_SCISSOR_TEST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER,source);int oldRead=glGetInteger(GL_READ_BUFFER);
        try{
            glReadBuffer(attachment);
            if(texture<0){texture=glGenTextures();framebuffer=glGenFramebuffers();}
            if(width!=w||height!=h){
                glBindTexture(GL_TEXTURE_2D,texture);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,0L);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,33071);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,33071);
                width=w;height=h;
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,framebuffer);glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            if(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE||glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)return false;
            glDisable(GL_SCISSOR_TEST);glBlitFramebuffer(x,y,x+w,y+h,0,0,w,h,GL_COLOR_BUFFER_BIT,GL_NEAREST);return true;
        }finally{
            if(scissor)glEnable(GL_SCISSOR_TEST);
            glReadBuffer(oldRead);glBindFramebuffer(GL_READ_FRAMEBUFFER,read);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);glBindTexture(GL_TEXTURE_2D,bound);
        }
    }
    public void close(){if(framebuffer!=0)glDeleteFramebuffers(framebuffer);if(texture>=0)glDeleteTextures(texture);texture=-1;framebuffer=width=height=0;}
}
