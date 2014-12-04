/*
 * Copyright (C) 2014 API Design
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.apidesign.bck2brwsr.dew.javac;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.JavaFileObject;
import org.apidesign.bck2brwsr.dew.javac.ClassLoaderFileManager.CPEntry;

final class Zips {
    private static Map<CPEntry,ZipInfo> cache = 
        new HashMap<>();

    synchronized static ZipInfo find(CPEntry e) {
        ZipInfo zi = cache.get(e);
        if (zi == null) {
            zi = new ZipInfo(e);
            cache.put(e, zi);
        }
        return zi;
    }
    
    static final class ZipInfo {
        final CPEntry entry;
        private byte[] data;
        private Entry[] entries;

        public ZipInfo(CPEntry entry) {
            this.entry = entry;
        }
        
        final void listResources(String folder, List<JavaFileObject> arr) throws IOException {
            if (data == null) {
                URL local = new URL("file:///home/jarda/.m2/repository/");
                URL online = new URL("https://repo1.maven.org/maven2/");

                String relative = entry.groupId.replace('.', '/') + "/"
                        + entry.artifactId + "/" + entry.version + "/"
                        + entry.artifactId + "-" + entry.version;
                if (entry.spec != null) {
                    relative += "-" + entry.spec;
                }
                relative += ".jar";

                try {
                    data = readURL(local, relative);
                } catch (IOException ex) {
                    data = readURL(online, relative);
                }
                entries = list(data);
            }

            int size = entries.length;
            for (int i = 0; i < size; i++) {
                Entry ze = entries[i];
                if (ze == null) {
                    break;
                }
                if (ze.path.startsWith(folder)) {
                    String rest = ze.path.substring(folder.length());
                    if (rest.startsWith("/")) {
                        rest = rest.substring(1);
                    }
                    if (rest.isEmpty() || rest.indexOf('/') >= 0) {
                        continue;
                    }
                    arr.add(ze);
                }
            }
        }

        private byte[] readURL(URL m2, String relative) throws IOException, MalformedURLException {
            try (InputStream is = new URL(m2, relative).openStream()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                for (;;) {
                    byte[] tmp = new byte[4096 * 8];
                    int len = is.read(tmp);
                    if (len == -1) {
                        break;
                    }
                    os.write(tmp, 0, len);
                }
                return os.toByteArray();
            }
        }
        
        private static int GIVE_UP = 1<<16;

        private final class Entry extends BaseFileObject {
            final long offset;

            Entry (String name, long offset, long time) {
                super(name, getKind(name));
                this.offset = offset;
            }
            
            @Override
            public InputStream openInputStream() throws IOException {
                return getInputStream(this);
            }

            @Override
            public OutputStream openOutputStream() throws IOException {
                throw new UnsupportedOperationException("Read Only FileObject");    //NOI18N
            }

            @Override
            public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
                return new InputStreamReader(openInputStream());
            }

            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                final BufferedReader in = new BufferedReader(openReader(ignoreEncodingErrors));
                try {
                    final StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                        sb.append('\n');    //NOI18N
                    }
                    return sb.toString();
                } finally {
                    in.close();
                }
            }

            @Override
            public Writer openWriter() throws IOException {
                return new OutputStreamWriter(openOutputStream());
            }

            @Override
            public long getLastModified() {
                return System.currentTimeMillis();
            }

            @Override
            public boolean delete() {
                return false;
            }
        } // end of Entry

        public InputStream getInputStream (final Entry e) throws IOException {
            return getInputStream(data, e.offset);
        }

        private static InputStream getInputStream (byte[] arr, final long offset) throws IOException {
            ByteArrayInputStream is = new ByteArrayInputStream(arr);
            is.skip(offset);
            ZipInputStream in = new ZipInputStream (is);
            ZipEntry e = in.getNextEntry();
            if (e != null && e.getCrc() == 0L && e.getMethod() == ZipEntry.STORED) {
                int cp = arr.length - is.available();
                return new ByteArrayInputStream(arr, cp, (int)e.getSize());
            }
            return in;
        }

        private Entry[] list(byte[] arr) throws IOException {
            final int size = arr.length;

            int at = size - ZipInputStream.ENDHDR;

            byte[] data = new byte[ZipInputStream.ENDHDR];        
            int giveup = 0;

            do {
                System.arraycopy(arr, at, data, 0, data.length);
                at--;
                giveup++;
                if (giveup > GIVE_UP) {
                    throw new IOException ();
                }
            } while (getsig(data) != ZipInputStream.ENDSIG);


            final long censize = endsiz(data);
            final long cenoff  = endoff(data);
            at = (int) cenoff;                                                     

            Entry[] result = new Entry[0];
            int cenread = 0;
            data = new byte[ZipInputStream.CENHDR];
            while (cenread < censize) {
                System.arraycopy(arr, at, data, 0, data.length);
                at += data.length;
                if (getsig(data) != ZipInputStream.CENSIG) {
                    throw new IOException("No central table");          //NOI18N
                }
                int cennam = cennam(data);
                int cenext = cenext(data);
                int cencom = cencom(data);
                long lhoff = cenoff(data);
                long centim = centim(data);
                String name = new String(arr, at, cennam, "UTF-8");
                at += cennam;
                int seekby = cenext+cencom;
                int cendatalen = ZipInputStream.CENHDR + cennam + seekby;
                cenread+=cendatalen;
                result = addEntry(result, new Entry(name,lhoff, centim));
                at += seekby;
            }
            return result;
        }

        private static Entry[] addEntry(Entry[] result, Entry entry) {
            Entry[] e = new Entry[result.length + 1];
            e[result.length] = entry;
            System.arraycopy(result, 0, e, 0, result.length);
            return e;
        }

        private static long getsig(final byte[] b) throws IOException {return get32(b,0);}
        private static long endsiz(final byte[] b) throws IOException {return get32(b,ZipInputStream.ENDSIZ);}
        private static long endoff(final byte[] b) throws IOException {return get32(b,ZipInputStream.ENDOFF);}
        private static long centim(final byte[] b) throws IOException {return get32(b,ZipInputStream.CENTIM);}
        private static int  cennam(final byte[] b) throws IOException {return get16(b,ZipInputStream.CENNAM);}
        private static int  cenext(final byte[] b) throws IOException {return get16(b,ZipInputStream.CENEXT);}
        private static int  cencom(final byte[] b) throws IOException {return get16(b,ZipInputStream.CENCOM);}
        private static long cenoff (final byte[] b) throws IOException {return get32(b,ZipInputStream.CENOFF);}

        private static int get16(final byte[] b, int off) throws IOException {        
            final int b1 = b[off];
            final int b2 = b[off+1];
            return (b1 & 0xff) | ((b2 & 0xff) << 8);
        }

        private static long get32(final byte[] b, int off) throws IOException {
            final int s1 = get16(b, off);
            final int s2 = get16(b, off+2);
            return s1 | ((long)s2 << 16);
        }
    }
}
