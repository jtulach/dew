/**
 * Development Environment for Web
 * Copyright (C) 2012-2013 Jaroslav Tulach <jaroslav.tulach@apidesign.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://opensource.org/licenses/GPL-2.0.
 */
package org.apidesign.bck2brwsr.dew.javac;

import java.net.URI;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;

/**
 *
 * @author Tomas Zezula
 */
public abstract class BaseFileObject implements InferableJavaFileObject {

    protected final String path;
    protected final Kind kind;

    BaseFileObject(
        String path,
        Kind kind) {
        if (path.startsWith("/")) {    //NOI18N
            throw new IllegalArgumentException("Path cannot start with /"); //NOI18N
        }
        this.path = path;
        this.kind = kind;
    }


    @Override
    public String infer() {
        return ClassLoaderFileManager.convertResourceToFQN(path);
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return this.kind == kind && getSimpleName(path, true).equals(simpleName);
    }

    @Override
    public NestingKind getNestingKind() {
        return null;
    }

    @Override
    public Modifier getAccessLevel() {
        return null;
    }

    @Override
    public URI toUri() {
        return URI.create(escape(path));
    }

    @Override
    public String getName() {
        return path;
    }



    protected static String getSimpleName(String path, boolean removeExtension) {
        int slashIndex = path.lastIndexOf('/'); //NOI18N
        assert slashIndex != 0;
        String name;
        if (slashIndex < 0) {
            name = path;
        } else {
            assert slashIndex + 1 < path.length();
            name = path.substring(slashIndex + 1);
        }
        if (removeExtension) {
            int indx = name.lastIndexOf('.');
            if (indx > 0) {
                return name.substring(0, indx);
            }
        }
        return name;
    }

    protected static Kind getKind(final String path) {
        final String simpleName = getSimpleName(path, false);
        final int dotIndex = simpleName.lastIndexOf('.'); //NOI18N
        final String ext = dotIndex > 0 ?
            simpleName.substring(dotIndex) :
            "";
        for (Kind k : Kind.values()) {
            if (k.extension.equals(ext)) {
                return k;
            }
        }
        return Kind.OTHER;
    }

    private String escape(String path) {
        return path;
    }


}
