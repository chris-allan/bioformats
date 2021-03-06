//
// TiffWriterTest.java
//

/*
LOCI software automated test suite for TestNG. Copyright (C) 2007-@year@
Jean-Marie Burel. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
  * Neither the name of the UW-Madison LOCI nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE UW-MADISON LOCI ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package loci.tests.testng;

import static org.testng.AssertJUnit.fail;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import loci.common.services.ServiceFactory;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.in.TiffReader;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataConverter;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffCompression;

/**
 * Tests writing of tiles in a tiff.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/test-suite/src/loci/tests/testng/TiffWriterTest.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/test-suite/src/loci/tests/testng/TiffWriterTest.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Jean-Marie Burel <j dot burel at dundee dot ac dot uk>
 */
public class TiffWriterTest {

  /** Reader */
  private IFormatReader reader;

  /** The metadata store. */
  private IMetadata metadata;

  /** Service to create the metadata store. */
  private OMEXMLService service;

  /** The compression levels to test. */
  private final static String[] COMPRESSION;

  /** The big tiff flags. */
  private final static Boolean[] BIG_TIFF;
  
  static {
    COMPRESSION = new String[2];
    COMPRESSION[0] = TiffCompression.UNCOMPRESSED.getCodecName();
    COMPRESSION[1] = TiffCompression.JPEG_2000.getCodecName();
    BIG_TIFF = new Boolean[1];
    BIG_TIFF[0] = Boolean.valueOf(false);
    //BIG_TIFF[1] = Boolean.valueOf(true);
  }

  /**
   * Initializes the writer.
   * @param output The file where to write the compressed data.
   * @param compression The compression to use.
   * @param bigTiff Pass <code>true</code> to set the <code>bigTiff</code> flag,
   *                <code>false</code> otherwise.
   * @return See above.
   * @throws Exception Thrown if an error occurred.
   */
  private TiffWriter initializeWriter(String output, String compression, boolean
      bigTiff)
  throws Exception {
    IMetadata newMetadata = service.createOMEXMLMetadata();
    MetadataConverter.convertMetadata(metadata, newMetadata);
    TiffWriter writer = new TiffWriter();
    writer.setMetadataRetrieve(newMetadata);
    writer.setCompression(compression);
    writer.setWriteSequentially(true);
    writer.setInterleaved(true);
    writer.setBigTiff(bigTiff);
    writer.setId(output);
    return writer;
  }

  /**
   * Tests the writing of the tiles.
   * @param output The output where to write the data.
   * @param compression The compression to use.
   * @param n The value by which to divide the width of the image.
   * @param m The value by which to divide the height of the image.
   * @param bigTiff Pass <code>true</code> to set the <code>bigTiff</code> flag,
   *                <code>false</code> otherwise.
   */
  private void assertTiles(String output, String compression, int n, int m, 
      boolean bigTiff) 
    throws Exception {
    TiffWriter writer = initializeWriter(output, compression, bigTiff);
    byte[] plane;
    int x, y;
    byte[] tile;
    int index = 0;
    long[] rowPerStrip;
    int w, h;
    IFD ifd;
    int count;
    Map<Integer, String> md5PerImage;
    Map<Integer, Map<Integer, String>> 
      md5ImageInSeries = new HashMap<Integer, Map<Integer, String>>();
    int v;
    int series = reader.getSeriesCount();
    for (int s = 0; s < series; s++) {
      reader.setSeries(s);
      w = reader.getSizeX()/n;
      h = reader.getSizeY()/m;
      rowPerStrip = new long[1];
      rowPerStrip[0] = h;
      count = reader.getImageCount();
      for (int k = 0; k < count; k++) {
        md5PerImage = new HashMap<Integer, String>();
        v = s*series+k;
        md5ImageInSeries.put(v, md5PerImage);
        ifd = new IFD();
        ifd.put(IFD.TILE_WIDTH, w);
        ifd.put(IFD.TILE_LENGTH, h);
        ifd.put(IFD.ROWS_PER_STRIP, rowPerStrip);
        plane = reader.openBytes(k); //read the plane.
        for (int i = 0; i < m; i++) {
          y = h*i;
          for (int j = 0; j < n; j++) {
            x = w*j;
            index = n*y+x;
            tile = reader.openBytes(k, x, y, w, h);
            md5PerImage.put(index, TestTools.md5(tile));
            writer.saveBytes(k, plane, ifd, x, y, w, h);
          }
        }
      }
    }
    writer.close();
    //Now going to read the output.
    TiffReader outputReader = new TiffReader();
    outputReader.setId(output);
    
    //first series.
    String planeDigest;
    String tileDigest;
    Map<Integer, String> results;
    for (int s = 0; s < series; s++) {
      outputReader.setSeries(s);
      count = outputReader.getImageCount();
      h = outputReader.getSizeY()/m;
      w = outputReader.getSizeX()/n;
      for (int k = 0; k < count; k++) {
        v = s*series+k;
        results = md5ImageInSeries.get(v);
        for (int i = 0; i < m; i++) {
          y = h*i;
          for (int j = 0; j < n; j++) {
            x = w*j;
            index = n*y+x;
            tile = outputReader.openBytes(k, x, y, w, h);
            planeDigest = results.get(index);
            tileDigest = TestTools.md5(tile);
            if (!planeDigest.equals(tileDigest)) {
              fail("Compression: "+compression+" "+
                  String.format("MD5:%d;%d;%d;%d;%d;%d %s != %s",
                  s, k, x, y, w, h, planeDigest, tileDigest));
            }
          }
        }
      }
    }
    outputReader.close();
  }

  /**
   * Tests the writing of the tiles.
   * @param output The output where to write the data.
   * @param compression The compression to use.
   * @param blockWidth The width of block to write.
   * @param blockHeight The height of block to write.
   * @param bigTiff Pass <code>true</code> to set the <code>bigTiff</code> flag,
   *                <code>false</code> otherwise.
   */
  private void assertUnevenTiles(String output, String compression, 
      int blockWidth, int blockHeight, boolean bigTiff) 
    throws Exception {
    TiffWriter writer = initializeWriter(output, compression, bigTiff);
    byte[] plane;
    int x, y;
    byte[] tile;
    int index = 0;
    long[] rowPerStrip;
    int w, h;
    IFD ifd;
    int count;
    Map<Integer, String> md5PerImage;
    Map<Integer, Map<Integer, String>> 
      md5ImageInSeries = new HashMap<Integer, Map<Integer, String>>();
    int v;
    int sizeX, sizeY;
    int n, m;
    int diffWidth, diffHeight;
    int series = reader.getSeriesCount();
    for (int s = 0; s < series; s++) {
      reader.setSeries(s);
      sizeX = reader.getSizeX();
      sizeY = reader.getSizeY();
      if (blockWidth <= 0) blockWidth = sizeX;
      if (blockHeight <= 0) blockHeight = sizeY;
      n = sizeX/blockWidth;
      m = sizeY/blockHeight;
      if (n == 0) {
        blockWidth = sizeX;
        n = 1;
      }
      if (m == 0) {
        blockHeight = sizeY;
        m = 1;
      }
      diffWidth = sizeX-n*blockWidth;
      diffHeight = sizeY-m*blockHeight;
      if (diffWidth > 0) n++;
      if (diffHeight > 0) m++;
      rowPerStrip = new long[1];
      rowPerStrip[0] = blockHeight;
      count = reader.getImageCount();
      for (int k = 0; k < count; k++) {
        x = 0;
        y = 0;
        v = s*series+k;
        md5PerImage = new HashMap<Integer, String>();
        md5ImageInSeries.put(v, md5PerImage);
        ifd = new IFD();
        ifd.put(IFD.TILE_WIDTH, blockWidth);
        ifd.put(IFD.TILE_LENGTH, blockHeight);
        ifd.put(IFD.ROWS_PER_STRIP, rowPerStrip);
        plane = reader.openBytes(k); //read the plane.
        index = 0;
        for (int i = 0; i < m; i++) {
          if (diffHeight > 0 && i == (m-1)) {
            y = sizeY-diffHeight;
            h = diffHeight;
          } else {
            y = blockHeight*i;
            h = blockHeight;
          }
          for (int j = 0; j < n; j++) {
            if (diffWidth > 0 && j == (n-1)) {
              x = sizeX-diffWidth;
              w = diffWidth;
            } else {
              x = blockWidth*j;
              w = blockWidth;
            }
            tile = reader.openBytes(k, x, y, w, h);
            String value = TestTools.md5(tile);
            writer.saveBytes(0, plane, ifd, x, y, w, h);
            md5PerImage.put(index, value);
            index++;
          }
        }
      }
    }
    writer.close();
    //Now going to read the output.
    TiffReader outputReader = new TiffReader();
    outputReader.setId(output);
    
    //first series.
    String planeDigest;
    String tileDigest;
    Map<Integer, String> results;
    for (int s = 0; s < series; s++) {
      outputReader.setSeries(s);
      count = outputReader.getImageCount();
      for (int k = 0; k < count; k++) {
        v = s*series+k;
        results = md5ImageInSeries.get(v);
        index = 0;
        sizeX = outputReader.getSizeX();
        sizeY = outputReader.getSizeY();
        n = sizeX/blockWidth;
        m = sizeY/blockHeight;
        diffWidth = sizeX-n*blockWidth;
        diffHeight = sizeY-m*blockHeight;
        if (diffWidth > 0) n++;
        if (diffHeight > 0) m++;
        for (int i = 0; i < m; i++) {
          if (diffHeight > 0 && i == (m-1)) {
            y = sizeY-diffHeight;
            h = diffHeight;
          } else {
            y = blockHeight*i;
            h = blockHeight;
          }
          for (int j = 0; j < n; j++) {
            if (diffWidth > 0 && j == (n-1)) {
              x = sizeX-diffWidth;
              w = diffWidth;
            } else {
              x = blockWidth*j;
              w = blockWidth;
            }
            tile = outputReader.openBytes(k, x, y, w, h);
            planeDigest = results.get(index);
            tileDigest = TestTools.md5(tile);
            if (!planeDigest.equals(tileDigest)) {
              fail("Compression: "+compression+" "+
                  String.format("MD5:%d;%d;%d;%d;%d;%d %s != %s",
                  s, k, x, y, w, h, planeDigest, tileDigest));
            }
            index++;
          }
        }
      }
    }
    outputReader.close();
  }

  @Parameters({"id"})
  @BeforeClass
  public void parse( String id) throws Exception {
    ServiceFactory factory = new ServiceFactory();
    service = factory.getInstance(OMEXMLService.class);
    metadata = service.createOMEXMLMetadata();
    reader = new ImageReader();
    reader.setMetadataStore(metadata);
    reader.setId(id);
  }

  @AfterClass
  public void tearDown() throws Exception {
    reader.close();
  }

  /**
   * Tests the writing of the full size image as JPEG200 stream.
   * @throws Exception Throw if an error occurred while writing.
   */
  @Test(enabled=true)
  public void testWriteFullImage() throws Exception {
    File f;
    for (int i = 0; i < COMPRESSION.length; i++) {
      for (int j = 0; j < BIG_TIFF.length; j++) {
        f =  File.createTempFile("testWriteFullImage_"+j+"_"+
            COMPRESSION[i], ".tiff");
        assertTiles(f.getAbsolutePath(), COMPRESSION[i], 1, 1, BIG_TIFF[j]);
      }
    }
  }

  /**
   * Tests the writing of the image divided in 4 blocks.
   * @throws Exception Throw if an error occurred while writing.
   */
  @Test(enabled=true)
  public void testWriteImageFourTiles() throws Exception {
    File f;
    for (int i = 0; i < COMPRESSION.length; i++) {
      f =  File.createTempFile("testWriteImageFourTiles_"+
          COMPRESSION[i], ".tiff");
      assertTiles(f.getAbsolutePath(), COMPRESSION[i], 2, 2, false);
    }
  }

  /**
   * Tests the writing of the image with 2 tiles with full width.
   * @throws Exception Throw if an error occurred while writing.
   */
  @Test(enabled=true)
  public void testWriteImageSplitHorizontal() throws Exception {
    File f;
    for (int i = 0; i < COMPRESSION.length; i++) {
      f =  File.createTempFile("testWriteImageSplitHorizontal_"+
          COMPRESSION[i], ".tiff");
      assertTiles(f.getAbsolutePath(), COMPRESSION[i], 1, 2, false);
    }
  }

  /**
   * Tests the writing of the image with 2 tiles with full height.
   * @throws Exception Throw if an error occurred while writing.
   */
  @Test(enabled=true)
  public void testWriteImageSplitVertical() throws Exception {
    File f;
    for (int i = 0; i < COMPRESSION.length; i++) {
      f =  File.createTempFile("testWriteImageSplitVertical_"+
          COMPRESSION[i], ".tiff");
      assertTiles(f.getAbsolutePath(), COMPRESSION[i], 2, 1, false);
    }
  }

  /**
   * Tests the writing of blocks of 256x256. Tiles should be square and size
   * multiple of 16.
   * @throws Exception Throw if an error occurred while writing.
   */
  @Test(enabled=true)
  public void testWriteUnevenTilesImage128x128Block() throws Exception {
    File f;
    for (int i = 0; i < COMPRESSION.length; i++) {
      for (int j = 0; j < BIG_TIFF.length; j++) {
        f =  File.createTempFile("testWriteUnevenTilesImage128x128Block_"+j+"_"+
            COMPRESSION[i], ".tiff");
        assertUnevenTiles(f.getAbsolutePath(), COMPRESSION[i], 128, 128, 
            BIG_TIFF[j]);
      }
    }
  }
  
  /**
   * Tests the writing of blocks of 256x256. Tiles should be square and size
   * multiple of 16.
   * @throws Exception Throw if an error occurred while writing.
   */
  @Test(enabled=true)
  public void testWriteUnevenTilesImage256x256Block() throws Exception {
    File f;
    for (int i = 0; i < COMPRESSION.length; i++) {
      for (int j = 0; j < BIG_TIFF.length; j++) {
        f =  File.createTempFile("testWriteUnevenTilesImage256x256Block_"+j+"_"+
            COMPRESSION[i], ".tiff");
        assertUnevenTiles(f.getAbsolutePath(), COMPRESSION[i], 256, 256, 
            BIG_TIFF[j]);
      }
    }
  }

}
