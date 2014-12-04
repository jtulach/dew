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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.tools.JavaFileObject;
import org.apidesign.bck2brwsr.dew.javac.ClassLoaderFileManager.CPEntry;

final class Zips {
    private static final Map<CPEntry,ZipInfo> cache = 
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

        public ZipInfo(CPEntry entry) {
            this.entry = entry;
        }
        
        final void listResources(String folder, List<JavaFileObject> arr) throws IOException {
            if (data == null) {
                URL m2 = new URL("file:///home/jarda/.m2/repository/");

                String relative = entry.groupId.replace('.', '/') + "/"
                        + entry.artifactId + "/" + entry.version + "/"
                        + entry.artifactId + "-" + entry.version;
                if (entry.spec != null) {
                    relative += "-" + entry.spec;
                }
                relative += ".jar";

                URL artifact = new URL(m2, relative);
                InputStream is = artifact.openStream();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                for (;;) {
                    byte[] tmp = new byte[4096 * 8];
                    int len = is.read(tmp);
                    if (len == -1) {
                        break;
                    }
                    os.write(tmp, 0, len);
                }
                data = os.toByteArray();
                os.close();
                is.close();
            }

            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data));
            for (;;) {
                ZipEntry ze = zis.getNextEntry();
                if (ze == null) {
                    break;
                }
                if (ze.getName().startsWith(folder)) {
                    String rest = ze.getName().substring(folder.length());
                    if (rest.startsWith("/")) {
                        rest = rest.substring(1);
                    }
                    if (rest.isEmpty() || rest.indexOf('/') >= 0) {
                        continue;
                    }
                    byte[] data = new byte[(int) ze.getSize()];
                    int offset = 0;
                    while (offset < data.length) {
                        int read = zis.read(data, offset, data.length - offset);
                        if (read == -1) {
                            break;
                        }
                        offset += read;
                    }
                    arr.add(new ClassLoaderJavaFileObject(rest, data));
                }
            }
            zis.close();
        }
        
    }
}
