package com.bame.client.util;

import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.Collections;

public class ImageSelection implements Transferable {
    private final Image image;
    private final File file;

    public ImageSelection(Image image, File file) {
        this.image = image;
        this.file = file;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor)
            || DataFlavor.javaFileListFlavor.equals(flavor)
            || DataFlavor.stringFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (DataFlavor.imageFlavor.equals(flavor)) {
            return this.image;
        }
        if (DataFlavor.javaFileListFlavor.equals(flavor)) {
            return Collections.singletonList(this.file);
        }
        if (DataFlavor.stringFlavor.equals(flavor)) {
            return this.file.getAbsolutePath();
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
