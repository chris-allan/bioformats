//
// VisitechReader.java
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

package loci.formats.in;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

/**
 * VisitechReader is the file format reader for Visitech XYS files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/VisitechReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/VisitechReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class VisitechReader extends FormatReader {

  // -- Constants --

  public static final String[] HTML_SUFFIX = {"html"};

  public static final String HEADER_MARKER = "[USE SAME FILE]";
  public static final byte[] PIXELS_MARKER = new byte[] {
    0, 0, 0, 0, 0, 0, (byte) 0xf0, 0x3f, 0, 0, 0, 0, 0, 0, (byte) 0xf0, 0x3f
  };

  // -- Fields --

  /** Files in this dataset. */
  private Vector<String> files;

  private long[] pixelOffsets;

  // -- Constructor --

  public VisitechReader() {
    super("Visitech XYS", new String[] {"xys", "html"});
    suffixSufficient = false;
    domains = new String[] {FormatTools.LM_DOMAIN};
    hasCompanionFiles = true;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#getOptimalTileHeight() */
  public int getOptimalTileHeight() {
    FormatTools.assertId(currentId, true, 1);
    return getSizeY();
  }

  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    if (checkSuffix(name, "xys")) return true;

    // verify that there is an .xys file in the same directory
    if (name.indexOf(" ") == -1) return false;
    if (!open) return false;
    String prefix = name.substring(0, name.lastIndexOf(" "));
    Location xys = new Location(prefix + " 1.xys");
    return xys.exists();
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    int plane = FormatTools.getPlaneSize(this);

    int div = getSizeZ() * getSizeT();
    int fileIndex = (series * getSizeC()) + no / div;
    int planeIndex = no % div;

    String file = files.get(fileIndex);
    RandomAccessInputStream s = new RandomAccessInputStream(file);
    s.order(isLittleEndian());
    s.seek(pixelOffsets[fileIndex]);

    int paddingBytes =
      (int) (s.length() - s.getFilePointer() - div * plane) / (div - 1);
    if (planeIndex > 0) {
      s.skipBytes((plane + paddingBytes) * planeIndex);
    }

    readPlane(s, x, y, w, h, buf);
    s.close();
    return buf;
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    Vector<String> v = new Vector<String>();
    v.add(currentId);
    if (!noPixels && files != null) {
      int nFiles = getSizeC();
      for (int i=0; i<nFiles; i++) {
        int index = getSeries() * nFiles + i;
        if (index < files.size()) {
          v.add(files.get(index));
        }
      }
    }
    return v.toArray(new String[v.size()]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      files = null;
      pixelOffsets = null;
    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);

    // first, make sure we have the HTML file

    if (!checkSuffix(id, HTML_SUFFIX)) {
      String base = id.substring(0, id.lastIndexOf(" "));

      currentId = null;
      initFile(base + " Report.html");
      return;
    }

    // parse the HTML file

    String s = DataTools.readFile(id);

    // strip out "style", "a", and "script" tags

    s = s.replaceAll("<[bB][rR]>", "\n");
    s = s.replaceAll("<[sS][tT][yY][lL][eE]\\p{ASCII}*?" +
      "[sS][tT][yY][lL][eE]>", "");
    s = s.replaceAll("<[sS][cC][rR][iI][pP][tT]\\p{ASCII}*?" +
      "[sS][cC][rR][iI][pP][tT]>", "");

    String key = null, value = null;
    int numSeries = 0;
    String[] tokens = s.split("\n");
    int estimatedSeriesCount = 0, estimatedSizeC = 0;
    for (String token : tokens) {
      token = token.trim();

      if ((token.startsWith("<") && !token.startsWith("</")) ||
        token.indexOf("pixels") != -1)
      {
        token = token.replaceAll("<.*?>", "");
        int ndx = token.indexOf(":");

        if (ndx != -1) {
          key = token.substring(0, ndx).trim();
          value = token.substring(ndx + 1).trim();

          if (key.equals("Number of steps")) {
            core[0].sizeZ = Integer.parseInt(value);
          }
          else if (key.equals("Image bit depth")) {
            int bits = Integer.parseInt(value);
            while ((bits % 8) != 0) bits++;
            bits /= 8;
            core[0].pixelType =
              FormatTools.pixelTypeFromBytes(bits, false, false);
          }
          else if (key.equals("Image dimensions")) {
            int n = value.indexOf(",");
            core[0].sizeX = Integer.parseInt(value.substring(1, n).trim());
            core[0].sizeY = Integer.parseInt(value.substring(n + 1,
              value.length() - 1).trim());
          }
          else if (key.startsWith("Channel Selection")) {
            core[0].sizeC++;
          }
          else if (key.startsWith("Microscope XY")) {
            numSeries++;
          }
          addGlobalMeta(key, value);
        }

        if (token.indexOf("pixels") != -1) {
          core[0].sizeC++;
          core[0].imageCount +=
            Integer.parseInt(token.substring(0, token.indexOf(" ")));
        }
        else if (token.startsWith("Time Series")) {
          int idx = token.indexOf(";") + 1;
          String ss = token.substring(idx, token.indexOf(" ", idx)).trim();
          core[0].sizeT = Integer.parseInt(ss);
        }
      }
      else if (token.indexOf("Document created") != -1) {
        estimatedSeriesCount++;
        estimatedSizeC++;
      }
    }

    if (numSeries == 0) {
      numSeries = estimatedSeriesCount;
      core[0].sizeC *= estimatedSizeC;
    }
    if (getSizeC() == 0) core[0].sizeC = estimatedSizeC;

    if (getSizeC() == 0) core[0].sizeC = 1;
    if (getSizeZ() == 0) core[0].sizeZ = 1;

    // find pixels files - we think there is one channel per file

    files = new Vector<String>();

    int ndx = currentId.lastIndexOf(File.separator) + 1;
    String base = currentId.substring(ndx, currentId.lastIndexOf(" "));

    File f = new File(currentId).getAbsoluteFile();
    String file = f.exists() ? f.getParent() + File.separator : "";

    if (numSeries == 0) numSeries = 1;
    for (int i=0; i<getSizeC(); i++) {
      String pixelsFile = file + base + " " + (i + 1) + ".xys";
      Location p = new Location(pixelsFile);
      if (p.exists()) files.add(p.getAbsolutePath());
      else {
        if (numSeries > 1) {
          core[0].sizeC -= (getSizeC() / numSeries);
          numSeries--;
        }
        else if (getSizeC() > 1) core[0].sizeC--;
      }
    }
    files.add(currentId);

    if (getSizeT() == 0) {
      core[0].sizeT = getImageCount() / (getSizeZ() * getSizeC());
      if (getSizeT() == 0) core[0].sizeT = 1;
    }

    if (getImageCount() == 0) {
      core[0].imageCount = getSizeZ() * getSizeC() * getSizeT();
    }
    if (numSeries > 1) {
      int x = getSizeX();
      int y = getSizeY();
      int z = getSizeZ();
      int c = getSizeC() / numSeries;
      int t = getSizeT();
      int count = z * c * t;
      int ptype = getPixelType();
      core = new CoreMetadata[numSeries];
      for (int i=0; i<numSeries; i++) {
        core[i] = new CoreMetadata();
        core[i].sizeX = x;
        core[i].sizeY = y;
        core[i].sizeZ = z;
        core[i].sizeC = c;
        core[i].sizeT = t;
        core[i].imageCount = count;
        core[i].pixelType = ptype;
      }
    }

    for (int i=0; i<getSeriesCount(); i++) {
      core[i].rgb = false;
      core[i].dimensionOrder = "XYZTC";
      core[i].interleaved = false;
      core[i].littleEndian = true;
      core[i].indexed = false;
      core[i].falseColor = false;
      core[i].metadataComplete = true;
    }

    pixelOffsets = new long[files.size() - 1];
    for (int i=0; i<pixelOffsets.length; i++) {
      pixelOffsets[i] = findPixelsOffset(i);
    }

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);
    for (int i=0; i<numSeries; i++) {
      store.setImageName("Position " + (i + 1), i);
      MetadataTools.setDefaultCreationDate(store, id, i);
    }
  }

  // -- Helper methods --

  private long findPixelsOffset(int fileIndex) throws IOException {
    String file = files.get(fileIndex);
    RandomAccessInputStream s = new RandomAccessInputStream(file);
    s.order(isLittleEndian());
    s.findString(false, HEADER_MARKER);

    int plane = FormatTools.getPlaneSize(this);
    int planeCount = getSizeZ() * getSizeT();

    long skip =
      (s.length() - s.getFilePointer() - (planeCount * plane)) / planeCount;
    long fp = s.getFilePointer() + skip - HEADER_MARKER.length();
    s.seek(fp);
    if (s.readByte() == PIXELS_MARKER[PIXELS_MARKER.length - 1]) {
      fp++;
    }
    s.close();
    return fp;
  }

}
