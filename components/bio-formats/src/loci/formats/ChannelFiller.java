//
// ChannelFiller.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.io.IOException;

import loci.formats.meta.MetadataStore;

/**
 * For indexed color data representing true color, factors out
 * the indices, replacing them with the color table values directly.
 *
 * For all other data (either non-indexed, or indexed with
 * "false color" tables), does nothing.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/ChannelFiller.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/ChannelFiller.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class ChannelFiller extends ReaderWrapper {

  // -- Utility methods --

  /** Converts the given reader into a ChannelFiller, wrapping if needed. */
  public static ChannelFiller makeChannelFiller(IFormatReader r) {
    if (r instanceof ChannelFiller) return (ChannelFiller) r;
    return new ChannelFiller(r);
  }

  // -- Fields --

  /**
   * Whether to fill in the indices.
   * By default, indices are filled iff data not false color.
   */
  protected Boolean filled = null;

  /** Number of LUT components. */
  protected int lutLength;

  // -- Constructors --

  /** Constructs a ChannelFiller around a new image reader. */
  public ChannelFiller() { super(); }

  /** Constructs a ChannelFiller with a given reader. */
  public ChannelFiller(IFormatReader r) { super(r); }

  // -- ChannelFiller methods --

  /** Returns true if the indices are being factored out. */
  public boolean isFilled() {
    if (!reader.isIndexed()) return false; // cannot fill non-indexed color
    if (lutLength < 1) return false; // cannot fill when LUTs are missing
    return filled == null ? !reader.isFalseColor() : filled;
  }

  /** Toggles whether the indices should be factored out. */
  public void setFilled(boolean filled) {
    this.filled = filled;
  }

  // -- IFormatReader API methods --

  /* @see IFormatReader#getSizeC() */
  @Override
  public int getSizeC() {
    if (!isFilled()) return reader.getSizeC();
    return reader.getSizeC() * lutLength;
  }

  /* @see IFormatReader#isRGB() */
  @Override
  public boolean isRGB() {
    if (!isFilled()) return reader.isRGB();
    return getRGBChannelCount() > 1;
  }

  /* @see IFormatReader#isIndexed() */
  @Override
  public boolean isIndexed() {
    if (!isFilled()) return reader.isIndexed();
    return false;
  }

  /* @see IFormatReader#get8BitLookupTable() */
  @Override
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    if (!isFilled()) return reader.get8BitLookupTable();
    return null;
  }

  /* @see IFormatReader#get16BitLookupTable() */
  @Override
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    if (!isFilled()) return reader.get16BitLookupTable();
    return null;
  }

  /* @see IFormatReader#getChannelDimLengths() */
  @Override
  public int[] getChannelDimLengths() {
    int[] cLengths = reader.getChannelDimLengths();
    if (!isFilled()) return cLengths;

    // in the case of a single channel, replace rather than append
    if (cLengths.length == 1 && cLengths[0] == 1) cLengths = new int[0];

    // append filled dimension to channel dim lengths
    int[] newLengths = new int[1 + cLengths.length];
    newLengths[0] = lutLength;
    System.arraycopy(cLengths, 0, newLengths, 1, cLengths.length);
    return newLengths;
  }

  /* @see IFormatReader#getChannelDimTypes() */
  @Override
  public String[] getChannelDimTypes() {
    String[] cTypes = reader.getChannelDimTypes();
    if (!isFilled()) return cTypes;

    // in the case of a single channel, leave type unchanged
    int[] cLengths = reader.getChannelDimLengths();
    if (cLengths.length == 1 && cLengths[0] == 1) return cTypes;

    // append filled dimension to channel dim types
    String[] newTypes = new String[1 + cTypes.length];
    newTypes[0] = FormatTools.CHANNEL;
    System.arraycopy(cTypes, 0, newTypes, 1, cTypes.length);
    return newTypes;
  }

  /* @see IFormatReader#openBytes(int) */
  @Override
  public byte[] openBytes(int no) throws FormatException, IOException {
    return openBytes(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, byte[]) */
  @Override
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return openBytes(no, buf, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, int, int, int, int) */
  @Override
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    byte[] buf = new byte[w * h * getRGBChannelCount() *
      FormatTools.getBytesPerPixel(getPixelType())];
    return openBytes(no, buf, x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    if (!isFilled()) return reader.openBytes(no, buf, x, y, w, h);

    // TODO: The pixel type should change to match the available color table.
    // That is, even if the indices are uint8, if the color table is 16-bit,
    // The pixel type should change to uint16. Similarly, if the indices are
    // uint16 but we are filling with an 8-bit color table, the pixel type
    // should change to uint8.

    // TODO: This logic below is opaque and could use some comments.

    byte[] pix = reader.openBytes(no, x, y, w, h);
    if (getPixelType() == FormatTools.UINT8) {
      byte[][] b = ImageTools.indexedToRGB(reader.get8BitLookupTable(), pix);
      if (isInterleaved()) {
        int pt = 0;
        for (int i=0; i<b[0].length; i++) {
          for (int j=0; j<b.length; j++) {
            buf[pt++] = b[j][i];
          }
        }
      }
      else {
        for (int i=0; i<b.length; i++) {
          System.arraycopy(b[i], 0, buf, i*b[i].length, b[i].length);
        }
      }
      return buf;
    }
    short[][] s = ImageTools.indexedToRGB(reader.get16BitLookupTable(),
      pix, isLittleEndian());

    if (isInterleaved()) {
      int pt = 0;
      for (int i=0; i<s[0].length; i++) {
        for (int j=0; j<s.length; j++) {
          buf[pt++] = (byte) (isLittleEndian() ?
            (s[j][i] & 0xff) : (s[j][i] >> 8));
          buf[pt++] = (byte) (isLittleEndian() ?
            (s[j][i] >> 8) : (s[j][i] & 0xff));
        }
      }
    }
    else {
      int pt = 0;
      for (int i=0; i<s.length; i++) {
        for (int j=0; j<s[i].length; j++) {
          buf[pt++] = (byte) (isLittleEndian() ?
            (s[i][j] & 0xff) : (s[i][j] >> 8));
          buf[pt++] = (byte) (isLittleEndian() ?
            (s[i][j] >> 8) : (s[i][j] & 0xff));
        }
      }
    }
    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#getNativeDataType() */
  public Class<?> getNativeDataType() {
    return byte[].class;
  }

  /* @see IFormatHandler#setId(String) */
  @Override
  public void setId(String id) throws FormatException, IOException {
    super.setId(id);
    lutLength = getLookupTableComponentCount();
    MetadataStore store = getMetadataStore();
    MetadataTools.populatePixels(store, this, false, false);
  }

  // -- Helper methods --

  /** Gets the number of color components in the lookup table. */
  private int getLookupTableComponentCount()
    throws FormatException, IOException
  {
    byte[][] lut8 = reader.get8BitLookupTable();
    if (lut8 != null) return lut8.length;
    short[][] lut16 = reader.get16BitLookupTable();
    if (lut16 != null) return lut16.length;
    // NB: For some formats, LUTs are plane-specific and will
    // only be available after opening a particular image plane.
    reader.openBytes(0, 0, 0, 1, 1); // read a single pixel, for performance
    lut8 = reader.get8BitLookupTable();
    if (lut8 != null) return lut8.length;
    lut16 = reader.get16BitLookupTable();
    if (lut16 != null) return lut16.length;
    return 0; // LUTs are missing
  }

}
