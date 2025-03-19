package net.maku.mascontrol.filter;

/**
 * Author:  zsd
 * Date:  2024/11/18/周一 15:59
 */

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * 返回值输出代理类
 *
 * @author kokJuis
 * @Title: ResponseWrapper
 * @Description:
 * @date 上午9:52:11
 */
public class ResponseWrapper extends HttpServletResponseWrapper {

    private ByteArrayOutputStream buffer;

    private ServletOutputStream out;

    public ResponseWrapper(HttpServletResponse httpServletResponse) {
        super(httpServletResponse);
        buffer = new ByteArrayOutputStream();
        out = new WrapperOutputStream(buffer);
    }

    @Override
    public ServletOutputStream getOutputStream()
            throws IOException {
        return out;
    }

    @Override
    public void flushBuffer()
            throws IOException {
        if (out != null) {
            out.flush();
        }
    }

    public byte[] getContent()
            throws IOException {
        flushBuffer();
        return buffer.toByteArray();
    }

    class WrapperOutputStream extends ServletOutputStream {
        private ByteArrayOutputStream bos;

        public WrapperOutputStream(ByteArrayOutputStream bos) {
            this.bos = bos;
        }

        @Override
        public void write(int b)
                throws IOException {
            bos.write(b);
        }

        @Override
        public boolean isReady() {

            // TODO Auto-generated method stub
            return false;

        }

        @Override
        public void setWriteListener(WriteListener arg0) {

            // TODO Auto-generated method stub

        }
    }

}
