//
// TiffSaver.java
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

package loci.formats.tiff;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.TreeSet;

import loci.common.ByteArrayHandle;
import loci.common.RandomAccessInputStream;
import loci.common.RandomAccessOutputStream;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.codec.CodecOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses TIFF data from an input source.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/tiff/TiffSaver.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/tiff/TiffSaver.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Eric Kjellman egkjellman at wisc.edu
 * @author Melissa Linkert melissa at glencoesoftware.com
 * @author Chris Allan callan at blackcat.ca
 */
public class TiffSaver {

  // -- Constructor --

  private static final Logger LOGGER = LoggerFactory.getLogger(TiffSaver.class);

  // -- Fields --

  /** Output stream to use when saving TIFF data. */
  protected RandomAccessOutputStream out;

  /** Input stream to use when overwriting data. */
  protected RandomAccessInputStream in;

  /** Whether or not to write BigTIFF data. */
  private boolean bigTiff = false;
  private boolean sequentialWrite = false;

  /** The codec options if set. */
  private CodecOptions options;
  
  // -- Constructors --

  /** Constructs a new TIFF saver from the given output source. */
  public TiffSaver(RandomAccessOutputStream out) {
    if (out == null) {
      throw new IllegalArgumentException(
          "Output stream expected to be not-null");
    }
    this.out = out;
  }

  /**
   * Creates a new instance.
   * @param filename The name of the file for output.
   * @throws IOException
   */
  public TiffSaver(String filename) throws IOException {
    this(new RandomAccessOutputStream(filename));
  }

  // -- TiffSaver methods --

  /**
   * Sets whether or not we know that the planes will be written sequentially.
   * If we are writing planes sequentially and set this flag, then performance
   * is slightly improved.
   */
  public void setWritingSequentially(boolean sequential) {
    sequentialWrite = sequential;
  }

  /** Sets the input stream. */
  public void setInputStream(RandomAccessInputStream in) {
    this.in = in;
  }

  /** Gets the stream from which TIFF data is being saved. */
  public RandomAccessOutputStream getStream() {
    return out;
  }

  /** Sets whether or not little-endian data should be written. */
  public void setLittleEndian(boolean littleEndian) {
    out.order(littleEndian);
  }

  /** Sets whether or not BigTIFF data should be written. */
  public void setBigTiff(boolean bigTiff) {
    this.bigTiff = bigTiff;
  }

  /** Returns whether or not we are writing little-endian data. */
  public boolean isLittleEndian() {
    return out.isLittleEndian();
  }

  /** Returns whether or not we are writing BigTIFF data. */
  public boolean isBigTiff() { return bigTiff; }

  /**
   * Sets the codec options.
   * @param options The value to set.
   */
  public void setCodecOptions(CodecOptions options) {
    this.options = options;
  }
  
  /** Writes the TIFF file header. */
  public void writeHeader() throws IOException {
    // write endianness indicator
    if (isLittleEndian()) {
      out.writeByte(TiffConstants.LITTLE);
      out.writeByte(TiffConstants.LITTLE);
    }
    else {
      out.writeByte(TiffConstants.BIG);
      out.writeByte(TiffConstants.BIG);
    }
    // write magic number
    if (bigTiff) {
      out.writeShort(TiffConstants.BIG_TIFF_MAGIC_NUMBER);
    }
    else out.writeShort(TiffConstants.MAGIC_NUMBER);

    // write the offset to the first IFD

    // for vanilla TIFFs, 8 is the offset to the first IFD
    // for BigTIFFs, 8 is the number of bytes in an offset
    out.writeInt(8);
    if (bigTiff) {
      // write the offset to the first IFD for BigTIFF files
      out.writeLong(16);
    }
  }

  /**
   */
  public void writeImage(byte[][] buf, IFDList ifds, int pixelType)
    throws FormatException, IOException
  {
    if (ifds == null) {
      throw new FormatException("IFD cannot be null");
    }
    if (buf == null) {
      throw new FormatException("Image data cannot be null");
    }
    for (int i=0; i<ifds.size(); i++) {
      if (i < buf.length) {
        writeImage(buf[i], ifds.get(i), i, pixelType, i == ifds.size() - 1);
      }
    }
  }

  /**
   */
  public void writeImage(byte[] buf, IFD ifd, int no, int pixelType,
    boolean last)
    throws FormatException, IOException
  {
    if (ifd == null) {
      throw new FormatException("IFD cannot be null");
    }
    int w = (int) ifd.getImageWidth();
    int h = (int) ifd.getImageLength();
    writeImage(buf, ifd, no, pixelType, 0, 0, w, h, last);
  }

  /**
   * Writes any rectangle form the passed image.
   *
   * @param buf The byte array that represents the image the full image.
   * @param ifd The Image File Directories. Mustn't be <code>null</code>.
   * @param no  The image index within the current file, starting from 0.
   * @param pixelType The type of pixels.
   * @param x   The X-coordinate of the top-left corner.
   * @param y   The Y-coordinate of the top-left corner.
   * @param w   The width of the rectangle.
   * @param h   The height of the rectangle.
   * @param last Pass <code>true</code> if it is the last image,
   *             <code>false</code> otherwise.
   * @throws FormatException
   * @throws IOException
   */
  public void writeImage(byte[] buf, IFD ifd, int no, int pixelType, int x,
      int y, int w, int h, boolean last)
  throws FormatException, IOException
  {
    //b/c method is public should check parameters again
    if (buf == null) {
      throw new FormatException("Image data cannot be null");
    }

    if (in == null) {
      throw new FormatException("RandomAccessInputStream is null. " +
      "Call setInputStream(RandomAccessInputStream) first.");
    }

    if (ifd == null) {
      throw new FormatException("IFD cannot be null");
    }

    int width = (int) ifd.getImageWidth();
    int height = (int) ifd.getImageLength();
    int bytesPerPixel = FormatTools.getBytesPerPixel(pixelType);
    int plane = width * height * bytesPerPixel;
    int nChannels = buf.length / (width * height * bytesPerPixel);
    boolean interleaved = ifd.getPlanarConfiguration() == 1;

    //boolean indexed = ifd.getIFDValue(IFD.COLOR_MAP) != null;

    makeValidIFD(ifd, pixelType, nChannels);

    // create pixel output buffers

    TiffCompression compression = ifd.getCompression();
    /*
    int rowsPerStrip = (int) ifd.getRowsPerStrip()[0];
    int stripSize = rowsPerStrip * w * bytesPerPixel;
    int nStrips = height/rowsPerStrip;//(height + rowsPerStrip-1) / rowsPerStrip;
    int vv = width/w;
    nStrips *= vv;
    if (interleaved) stripSize *= nChannels;
    else nStrips *= nChannels;
     */
    int tileWidth = (int) ifd.getTileWidth(); //make sure this is
    int tileHeight = (int) ifd.getTileLength();
    int rowsPerStrip = (int) ifd.getRowsPerStrip()[0]; //should = tileHeight
    //if (rowsPerStrip > h) rowsPerStrip = h;
    int stripSize = rowsPerStrip * tileWidth * bytesPerPixel; //w

    int nStrips = (int) (ifd.getTilesPerRow()*ifd.getTilesPerColumn());

    if (interleaved) stripSize *= nChannels;
    else nStrips *= nChannels;

    ByteArrayOutputStream[] stripBuf = new ByteArrayOutputStream[nStrips];
    DataOutputStream[] stripOut = new DataOutputStream[nStrips];
    for (int strip=0; strip<nStrips; strip++) {
      stripBuf[strip] = new ByteArrayOutputStream(stripSize);
      stripOut[strip] = new DataOutputStream(stripBuf[strip]);
    }
    int[] bps = ifd.getBitsPerSample();
    int off;
    // The strip we're actually to compress based on the number of
    // tiles/strips that we've configured and the dimensions of the data we've
    // been given.
    //int thisStrip = (y / h) * (width / w) + (x / w);
    int wn = width/tileWidth;
    int diff = width-(wn*tileWidth);
    int yR = y/tileHeight;
    int thisStrip = (y / tileHeight) * (width / tileWidth) + (x / tileWidth);
    if (diff > 0) thisStrip = thisStrip+yR; 

    // write pixel strips to output buffers
    for (int strip = 0; strip < nStrips; strip++) {
      if (strip != thisStrip) {
        continue;
      }
      for (int row=0; row<tileHeight; row++) {
        for (int col=0; col<tileWidth; col++) {
          int ndx = ((row+y) * width + col +x) * bytesPerPixel;
          for (int c=0; c<nChannels; c++) {
            for (int n=0; n<bps[c]/8; n++) {
              if (interleaved) {
                //off = ndx * nChannels + c * bytesPerPixel + n;
                //off = ndx + c * bytesPerPixel + n;
                off = ndx + c * plane + n;
                if (row >= h || col >= w) {
                  stripOut[strip].writeByte(0);
                } else {
                  stripOut[strip].writeByte(buf[off]);
                }
              }
              else {
                off = c * plane + ndx + n;
                if (row >= h || col >= w) {
                  stripOut[strip].writeByte(0);
                } else {
                  stripOut[c * (nStrips / nChannels) + strip].writeByte(
                      buf[off]);
                }
              }
            }
          }
        }
      }
    }


    // compress strips according to given differencing and compression schemes
    byte[][] strips = new byte[nStrips][];
    for (int strip=0; strip<nStrips; strip++) {
      if (strip != thisStrip) {
        continue;
      }
      strips[strip] = stripBuf[strip].toByteArray();
      TiffCompression.difference(strips[strip], ifd);
      CodecOptions codecOptions = compression.getCompressionCodecOptions(
          ifd, options);
      codecOptions.height = tileHeight;//rowsPerStrip;
      codecOptions.width = tileWidth;//w;
      strips[strip] = compression.compress(strips[strip], codecOptions);
    }

    if (!sequentialWrite) {
      TiffParser parser = new TiffParser(in);
      long[] ifdOffsets = parser.getIFDOffsets();
      if (no < ifdOffsets.length) {
        out.seek(ifdOffsets[no]);
        ifd = parser.getIFD(ifdOffsets[no]);
      }
    }

    // record strip byte counts and offsets

    long[] stripByteCounts = new long[nStrips];
    long[] stripOffsets = new long[nStrips];

    if (ifd.containsKey(IFD.STRIP_BYTE_COUNTS)) {
      long[] newStripByteCounts = ifd.getStripByteCounts();
      if (newStripByteCounts.length == nStrips) {
        stripByteCounts = newStripByteCounts;
      }
    }
    if (ifd.containsKey(IFD.STRIP_OFFSETS)) {
      long[] newStripOffsets = ifd.getStripOffsets();
      if (newStripOffsets.length == nStrips) {
        stripOffsets = newStripOffsets;
      }
    }

    for (int i=0; i<stripByteCounts.length; i++) {
      if (stripByteCounts[i] == 0 && strips[i] != null) {
        stripByteCounts[i] = strips[i].length;
      }
    }

    ifd.putIFDValue(IFD.STRIP_BYTE_COUNTS, stripByteCounts);
    ifd.putIFDValue(IFD.STRIP_OFFSETS, stripOffsets);

    long fp = out.getFilePointer();
    writeIFD(ifd, 0);

    for (int i=0; i<strips.length; i++) {
      if (stripOffsets[i] > 0 && stripByteCounts[i] != 0) {
        LOGGER.debug("Strip {} seeking to {}",
            i, stripOffsets[i] + stripByteCounts[i]);
        out.seek(stripOffsets[i] + stripByteCounts[i]);
      }
      else if (strips[i] != null) {
        stripOffsets[i] = out.getFilePointer();
        out.write(strips[i]);
      }
    }
    ifd.putIFDValue(IFD.STRIP_BYTE_COUNTS, stripByteCounts);
    ifd.putIFDValue(IFD.STRIP_OFFSETS, stripOffsets);
    long endFP = out.getFilePointer();
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Offset before IFD write: {} Seeking to: {}",
          out.getFilePointer(), fp);
    }
    out.seek(fp);

    writeIFD(ifd, last ? 0 : endFP);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Offset after IFD write: {}", out.getFilePointer());
    }
  }

  public void writeIFD(IFD ifd, long nextOffset)
    throws FormatException, IOException
  {
    TreeSet<Integer> keys = new TreeSet<Integer>(ifd.keySet());
    int keyCount = keys.size();

    if (ifd.containsKey(new Integer(IFD.LITTLE_ENDIAN))) keyCount--;
    if (ifd.containsKey(new Integer(IFD.BIG_TIFF))) keyCount--;
    if (ifd.containsKey(new Integer(IFD.REUSE))) keyCount--;

    long fp = out.getFilePointer();
    int bytesPerEntry = bigTiff ? TiffConstants.BIG_TIFF_BYTES_PER_ENTRY :
      TiffConstants.BYTES_PER_ENTRY;
    int ifdBytes = (bigTiff ? 16 : 6) + bytesPerEntry * keyCount;

    if (bigTiff) out.writeLong(keyCount);
    else out.writeShort(keyCount);

    ByteArrayHandle extra = new ByteArrayHandle();
    RandomAccessOutputStream extraStream = new RandomAccessOutputStream(extra);

    for (Integer key : keys) {
      if (key.equals(IFD.LITTLE_ENDIAN) || key.equals(IFD.BIG_TIFF) ||
          key.equals(IFD.REUSE)) continue;

      Object value = ifd.get(key);
      writeIFDValue(extraStream, ifdBytes + fp, key.intValue(), value);
    }
    if (bigTiff) out.seek(out.getFilePointer() - 8);
    writeIntValue(out, nextOffset);
    out.write(extra.getBytes(), 0, (int) extra.length());
  }

  /**
   * Writes the given IFD value to the given output object.
   * @param extraOut buffer to which "extra" IFD information should be written
   * @param offset global offset to use for IFD offset values
   * @param tag IFD tag to write
   * @param value IFD value to write
   */
  public void writeIFDValue(RandomAccessOutputStream extraOut, long offset,
    int tag, Object value)
    throws FormatException, IOException
  {
    extraOut.order(isLittleEndian());

    // convert singleton objects into arrays, for simplicity
    if (value instanceof Short) {
      value = new short[] {((Short) value).shortValue()};
    }
    else if (value instanceof Integer) {
      value = new int[] {((Integer) value).intValue()};
    }
    else if (value instanceof Long) {
      value = new long[] {((Long) value).longValue()};
    }
    else if (value instanceof TiffRational) {
      value = new TiffRational[] {(TiffRational) value};
    }
    else if (value instanceof Float) {
      value = new float[] {((Float) value).floatValue()};
    }
    else if (value instanceof Double) {
      value = new double[] {((Double) value).doubleValue()};
    }

    int dataLength = bigTiff ? 8 : 4;

    // write directory entry to output buffers
    out.writeShort(tag); // tag
    if (value instanceof short[]) {
      short[] q = (short[]) value;
      out.writeShort(IFDType.BYTE.getCode());
      writeIntValue(out, q.length);
      if (q.length <= dataLength) {
        for (int i=0; i<q.length; i++) out.writeByte(q[i]);
        for (int i=q.length; i<dataLength; i++) out.writeByte(0);
      }
      else {
        writeIntValue(out, offset + extraOut.length());
        for (int i=0; i<q.length; i++) extraOut.writeByte(q[i]);
      }
    }
    else if (value instanceof String) { // ASCII
      char[] q = ((String) value).toCharArray();
      out.writeShort(IFDType.ASCII.getCode()); // type
      writeIntValue(out, q.length + 1);
      if (q.length < dataLength) {
        for (int i=0; i<q.length; i++) out.writeByte(q[i]); // value(s)
        for (int i=q.length; i<dataLength; i++) out.writeByte(0); // padding
      }
      else {
        writeIntValue(out, offset + extraOut.length());
        for (int i=0; i<q.length; i++) extraOut.writeByte(q[i]); // values
        extraOut.writeByte(0); // concluding NULL byte
      }
    }
    else if (value instanceof int[]) { // SHORT
      int[] q = (int[]) value;
      out.writeShort(IFDType.SHORT.getCode()); // type
      writeIntValue(out, q.length);
      if (q.length <= dataLength / 2) {
        for (int i=0; i<q.length; i++) {
          out.writeShort(q[i]); // value(s)
        }
        for (int i=q.length; i<dataLength / 2; i++) {
          out.writeShort(0); // padding
        }
      }
      else {
        writeIntValue(out, offset + extraOut.length());
        for (int i=0; i<q.length; i++) {
          extraOut.writeShort(q[i]); // values
        }
      }
    }
    else if (value instanceof long[]) { // LONG
      long[] q = (long[]) value;

      int type = bigTiff ? IFDType.LONG8.getCode() : IFDType.LONG.getCode();
      out.writeShort(type);
      writeIntValue(out, q.length);

      if (q.length <= dataLength / 4) {
        for (int i=0; i<q.length; i++) {
          writeIntValue(out, q[0]);
        }
        for (int i=q.length; i<dataLength / 4; i++) {
          writeIntValue(out, 0);
        }
      }
      else {
        writeIntValue(out, offset + extraOut.length());
        for (int i=0; i<q.length; i++) {
          writeIntValue(extraOut, q[i]);
        }
      }
    }
    else if (value instanceof TiffRational[]) { // RATIONAL
      TiffRational[] q = (TiffRational[]) value;
      out.writeShort(IFDType.RATIONAL.getCode()); // type
      writeIntValue(out, q.length);
      if (bigTiff && q.length == 1) {
        out.writeInt((int) q[0].getNumerator());
        out.writeInt((int) q[0].getDenominator());
      }
      else {
        writeIntValue(out, offset + extraOut.length());
        for (int i=0; i<q.length; i++) {
          extraOut.writeInt((int) q[i].getNumerator());
          extraOut.writeInt((int) q[i].getDenominator());
        }
      }
    }
    else if (value instanceof float[]) { // FLOAT
      float[] q = (float[]) value;
      out.writeShort(IFDType.FLOAT.getCode()); // type
      writeIntValue(out, q.length);
      if (q.length <= dataLength / 4) {
        for (int i=0; i<q.length; i++) {
          out.writeFloat(q[0]); // value
        }
        for (int i=q.length; i<dataLength / 4; i++) {
          out.writeInt(0); // padding
        }
      }
      else {
        writeIntValue(out, offset + extraOut.length());
        for (int i=0; i<q.length; i++) {
          extraOut.writeFloat(q[i]); // values
        }
      }
    }
    else if (value instanceof double[]) { // DOUBLE
      double[] q = (double[]) value;
      out.writeShort(IFDType.DOUBLE.getCode()); // type
      writeIntValue(out, q.length);
      writeIntValue(out, offset + extraOut.length());
      for (int i=0; i<q.length; i++) {
        extraOut.writeDouble(q[i]); // values
      }
    }
    else {
      throw new FormatException("Unknown IFD value type (" +
        value.getClass().getName() + "): " + value);
    }
  }

  public void overwriteLastIFDOffset(RandomAccessInputStream raf)
    throws FormatException, IOException
  {
    if (raf == null)
      throw new FormatException("Output cannot be null");
    TiffParser parser = new TiffParser(raf);
    long[] offsets = parser.getIFDOffsets();
    out.seek(raf.getFilePointer() - (bigTiff ? 8 : 4));
    writeIntValue(out, 0);
  }

  /**
   * Surgically overwrites an existing IFD value with the given one. This
   * method requires that the IFD directory entry already exist. It
   * intelligently updates the count field of the entry to match the new
   * length. If the new length is longer than the old length, it appends the
   * new data to the end of the file and updates the offset field; if not, or
   * if the old data is already at the end of the file, it overwrites the old
   * data in place.
   */
  public void overwriteIFDValue(RandomAccessInputStream raf,
    int ifd, int tag, Object value) throws FormatException, IOException
  {
    if (raf == null)
      throw new FormatException("Output cannot be null");
    LOGGER.debug("overwriteIFDValue (ifd={}; tag={}; value={})",
      new Object[] {ifd, tag, value});

    raf.seek(0);
    TiffParser parser = new TiffParser(raf);
    Boolean valid = parser.checkHeader();
    if (valid == null) {
      throw new FormatException("Invalid TIFF header");
    }

    boolean little = valid.booleanValue();
    boolean bigTiff = parser.isBigTiff();

    setLittleEndian(little);
    setBigTiff(bigTiff);

    long offset = bigTiff ? 8 : 4; // offset to the IFD

    int bytesPerEntry = bigTiff ?
      TiffConstants.BIG_TIFF_BYTES_PER_ENTRY : TiffConstants.BYTES_PER_ENTRY;

    raf.seek(offset);

    // skip to the correct IFD
    long[] offsets = parser.getIFDOffsets();
    if (ifd >= offsets.length) {
      throw new FormatException(
        "No such IFD (" + ifd + " of " + offsets.length + ")");
    }
    raf.seek(offsets[ifd]);

    // get the number of directory entries
    long num = bigTiff ? raf.readLong() : raf.readUnsignedShort();

    // search directory entries for proper tag
    for (int i=0; i<num; i++) {
      raf.seek(offsets[ifd] + (bigTiff ? 8 : 2) + bytesPerEntry * i);

      TiffIFDEntry entry = parser.readTiffIFDEntry();
      if (entry.getTag() == tag) {
        // write new value to buffers
        ByteArrayHandle ifdBuf = new ByteArrayHandle(bytesPerEntry);
        RandomAccessOutputStream ifdOut = new RandomAccessOutputStream(ifdBuf);
        ByteArrayHandle extraBuf = new ByteArrayHandle();
        RandomAccessOutputStream extraOut =
          new RandomAccessOutputStream(extraBuf);
        extraOut.order(little);
        TiffSaver saver = new TiffSaver(ifdOut);
        saver.setLittleEndian(isLittleEndian());
        saver.writeIFDValue(extraOut, entry.getValueOffset(), tag, value);
        ifdBuf.seek(0);
        extraBuf.seek(0);

        // extract new directory entry parameters
        int newTag = ifdBuf.readShort();
        int newType = ifdBuf.readShort();
        int newCount;
        long newOffset;
        if (bigTiff) {
          newCount = ifdBuf.readInt() & 0xffffffff;
          newOffset = ifdBuf.readLong();
        }
        else {
          newCount = ifdBuf.readInt();
          newOffset = ifdBuf.readInt();
        }
        LOGGER.debug("overwriteIFDValue:");
        LOGGER.debug("\told ({});", entry);
        LOGGER.debug("\tnew: (tag={}; type={}; count={}; offset={})",
          new Object[] {newTag, newType, newCount, newOffset});

        // determine the best way to overwrite the old entry
        if (extraBuf.length() == 0) {
          // new entry is inline; if old entry wasn't, old data is orphaned
          // do not override new offset value since data is inline
          LOGGER.debug("overwriteIFDValue: new entry is inline");
        }
        else if (entry.getValueOffset() + entry.getValueCount() *
          entry.getType().getBytesPerElement() == raf.length())
        {
          // old entry was already at EOF; overwrite it
          newOffset = entry.getValueOffset();
          LOGGER.debug("overwriteIFDValue: old entry is at EOF");
        }
        else if (newCount <= entry.getValueCount()) {
          // new entry is as small or smaller than old entry; overwrite it
          newOffset = entry.getValueOffset();
          LOGGER.debug("overwriteIFDValue: new entry is <= old entry");
        }
        else {
          // old entry was elsewhere; append to EOF, orphaning old entry
          newOffset = raf.length();
          LOGGER.debug("overwriteIFDValue: old entry will be orphaned");
        }

        // overwrite old entry
        out.seek(offsets[ifd] + (bigTiff ? 8 : 2) + bytesPerEntry * i + 2);
        out.writeShort(newType);
        writeIntValue(out, newCount);
        writeIntValue(out, newOffset);
        if (extraBuf.length() > 0) {
          out.seek(newOffset);
          out.write(extraBuf.getByteBuffer(), 0, newCount);
        }
        return;
      }
    }

    throw new FormatException("Tag not found (" + IFD.getIFDTagName(tag) + ")");
  }

  /** Convenience method for overwriting a file's first ImageDescription. */
  public void overwriteComment(RandomAccessInputStream in, Object value)
    throws FormatException, IOException
  {
    overwriteIFDValue(in, 0, IFD.IMAGE_DESCRIPTION, value);
  }

  // -- Helper methods --

  /**
   * Write the given value to the given RandomAccessOutputStream.
   * If the 'bigTiff' flag is set, then the value will be written as an 8 byte
   * long; otherwise, it will be written as a 4 byte integer.
   */
  private void writeIntValue(RandomAccessOutputStream out, long offset)
    throws IOException
  {
    if (bigTiff) {
      out.writeLong(offset);
    }
    else {
      out.writeInt((int) offset);
    }
  }

  /**
   * Makes a valid IFD.
   *
   * @param ifd The IFD to handle.
   * @param pixelType The pixel type.
   * @param nChannels The number of channels.
   */
  private void makeValidIFD(IFD ifd, int pixelType, int nChannels) {
    int bytesPerPixel = FormatTools.getBytesPerPixel(pixelType);
    int bps = 8 * bytesPerPixel;
    int[] bpsArray = new int[nChannels];
    Arrays.fill(bpsArray, bps);
    ifd.putIFDValue(IFD.BITS_PER_SAMPLE, bpsArray);

    if (FormatTools.isFloatingPoint(pixelType)) {
      ifd.putIFDValue(IFD.SAMPLE_FORMAT, 3);
    }
    if (ifd.getIFDValue(IFD.COMPRESSION) == null) {
      ifd.putIFDValue(IFD.COMPRESSION, TiffCompression.UNCOMPRESSED.getCode());
    }

    boolean indexed = nChannels == 1 && ifd.getIFDValue(IFD.COLOR_MAP) != null;
    PhotoInterp pi = indexed ? PhotoInterp.RGB_PALETTE :
      nChannels == 1 ? PhotoInterp.BLACK_IS_ZERO : PhotoInterp.RGB;
    ifd.putIFDValue(IFD.PHOTOMETRIC_INTERPRETATION, pi.getCode());

    ifd.putIFDValue(IFD.SAMPLES_PER_PIXEL, nChannels);

    if (ifd.get(IFD.X_RESOLUTION) == null) {
      ifd.putIFDValue(IFD.X_RESOLUTION, new TiffRational(1, 1));
    }
    if (ifd.get(IFD.Y_RESOLUTION) == null) {
      ifd.putIFDValue(IFD.Y_RESOLUTION, new TiffRational(1, 1));
    }
    if (ifd.get(IFD.SOFTWARE) == null) {
      ifd.putIFDValue(IFD.SOFTWARE, "LOCI Bio-Formats");
    }
    if (ifd.get(IFD.ROWS_PER_STRIP) == null) {
      ifd.putIFDValue(IFD.ROWS_PER_STRIP, new long[] {1});
    }
    if (ifd.get(IFD.IMAGE_DESCRIPTION) == null) {
      ifd.putIFDValue(IFD.IMAGE_DESCRIPTION, "");
    }
  }

}
