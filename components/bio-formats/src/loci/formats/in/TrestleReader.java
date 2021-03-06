//
// TrestleReader.java
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

import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import loci.common.DataTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.codec.BitWriter;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffParser;

/**
 * TrestleReader is the file format reader for Trestle slide datasets.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/TrestleReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/TrestleReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class TrestleReader extends BaseTiffReader {

  // -- Constants --

  /** Logger for this class. */
  private static final Logger LOGGER =
    LoggerFactory.getLogger(TrestleReader.class);

  // -- Fields --

  private ArrayList<String> files;
  private String roiFile;
  private String roiDrawFile;

  // -- Constructor --

  /** Constructs a new Trestle reader. */
  public TrestleReader() {
    super("Trestle", new String[] {"tif"});
    domains = new String[] {FormatTools.HISTOLOGY_DOMAIN};
    suffixSufficient = false;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    TiffParser parser = new TiffParser(stream);
    IFD ifd = parser.getFirstIFD();
    if (ifd == null) return false;
    String copyright = ifd.getIFDTextValue(IFD.COPYRIGHT);
    if (copyright == null) return false;
    return copyright.indexOf("Trestle Corp.") >= 0;
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);

    if (noPixels) {
      return files.toArray(new String[files.size()]);
    }
    String[] allFiles = new String[files.size() + 1];
    files.toArray(allFiles);
    allFiles[allFiles.length - 1] = currentId;
    return allFiles;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    if (getSeriesCount() == 1) {
      return super.openBytes(no, buf, x, y, w, h);
    }
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);
    tiffParser.getSamples(ifds.get(series), buf, x, y, w, h);
    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      files = null;
    }
  }

  /* @see loci.formats.IFormatReader#getOptimalTileWidth() */
  public int getOptimalTileWidth() {
    FormatTools.assertId(currentId, true, 1);
    try {
      return (int) ifds.get(getSeries()).getTileWidth();
    }
    catch (FormatException e) {
      LOGGER.debug("", e);
    }
    return super.getOptimalTileWidth();
  }

  /* @see loci.formats.IFormatReader#getOptimalTileHeight() */
  public int getOptimalTileHeight() {
    FormatTools.assertId(currentId, true, 1);
    try {
      return (int) ifds.get(getSeries()).getTileLength();
    }
    catch (FormatException e) {
      LOGGER.debug("", e);
    }
    return super.getOptimalTileHeight();
  }

  // -- Internal BaseTiffReader API methods --

  /* @see loci.formats.BaseTiffReader#initStandardMetadata() */
  protected void initStandardMetadata() throws FormatException, IOException {
    super.initStandardMetadata();

    ifds = tiffParser.getIFDs();
    for (IFD ifd : ifds) {
      tiffParser.fillInIFD(ifd);
    }

    String comment = ifds.get(0).getComment();
    String[] values = comment.split(";");
    for (String v : values) {
      int eq = v.indexOf("=");
      if (eq < 0) continue;
      String key = v.substring(0, eq).trim();
      String value = v.substring(eq + 1).trim();
      addGlobalMeta(key, value);
    }

    core = new CoreMetadata[ifds.size()];

    for (int i=0; i<core.length; i++) {
      setSeries(i);
      core[i] = new CoreMetadata();

      if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      }
    }
    setSeries(0);

    // repopulate core metadata

    for (int s=0; s<core.length; s++) {
      IFD ifd = ifds.get(s);
      PhotoInterp p = ifd.getPhotometricInterpretation();
      int samples = ifd.getSamplesPerPixel();
      core[s].rgb = samples > 1 || p == PhotoInterp.RGB;

      core[s].sizeX = (int) ifd.getImageWidth();
      core[s].sizeY = (int) ifd.getImageLength();
      core[s].sizeZ = 1;
      core[s].sizeT = 1;
      core[s].sizeC = core[s].rgb ? samples : 1;
      core[s].littleEndian = ifd.isLittleEndian();
      core[s].indexed = p == PhotoInterp.RGB_PALETTE &&
        (get8BitLookupTable() != null || get16BitLookupTable() != null);
      core[s].imageCount = 1;
      core[s].pixelType = ifd.getPixelType();
      core[s].metadataComplete = true;
      core[s].interleaved = false;
      core[s].falseColor = false;
      core[s].dimensionOrder = "XYCZT";
      core[s].thumbnail = s > 0;
    }

    // look for all of the other associated metadata files

    files = new ArrayList<String>();

    Location baseFile = new Location(currentId).getAbsoluteFile();
    Location parent = baseFile.getParentFile();
    String name = baseFile.getName();
    if (name.indexOf(".") >= 0) {
      name = name.substring(0, name.indexOf(".") + 1);
    }

    roiFile = new Location(parent, name + "ROI").getAbsolutePath();
    roiDrawFile = new Location(parent, name + "ROI-draw").getAbsolutePath();

    String[] list = parent.list(true);
    for (String f : list) {
      if (!f.equals(baseFile.getName())) {
        files.add(new Location(parent, f).getAbsolutePath());
      }
    }
  }

  /* @see loci.formats.BaseTiffReader#initMetadataStore() */
  protected void initMetadataStore() throws FormatException {
    super.initMetadataStore();

    MetadataStore store = makeFilterMetadata();

    for (int i=0; i<getSeriesCount(); i++) {
      store.setImageName("Series " + (i + 1), i);
    }

    MetadataLevel level = getMetadataOptions().getMetadataLevel();
    if (level != MetadataLevel.MINIMUM) {
      if (level != MetadataLevel.NO_OVERLAYS) {
        try {
          parseROIs(store);
        }
        catch (IOException e) {
          LOGGER.debug("Could not parse ROIs", e);
        }
      }
    }
  }

  // -- Helper methods --

  private void parseROIs(MetadataStore store)
    throws FormatException, IOException
  {
    String roiID = MetadataTools.createLSID("ROI", 0, 0);
    String maskID = MetadataTools.createLSID("Shape", 0, 0);

    store.setROIID(roiID, 0);
    store.setMaskID(maskID, 0, 0);

    String positionData = DataTools.readFile(roiDrawFile);
    String[] coordinates = positionData.split("[ ,]");

    double x1 = Double.parseDouble(coordinates[1]);
    double y1 = Double.parseDouble(coordinates[2]);
    double x2 = Double.parseDouble(coordinates[3]);
    double y2 = Double.parseDouble(coordinates[5]);

    store.setMaskX(x1, 0, 0);
    store.setMaskY(y1, 0, 0);
    store.setMaskWidth(x2 - x1, 0, 0);
    store.setMaskHeight(y2 - y1, 0, 0);

    store.setImageROIRef(roiID, 0, 0);

    ImageReader roiReader = new ImageReader();
    roiReader.setId(roiFile);
    byte[] roiPixels = roiReader.openBytes(0);
    roiReader.close();

    BitWriter bits = new BitWriter(roiPixels.length / 8);
    for (int i=0; i<roiPixels.length; i++) {
      bits.write(roiPixels[i] == 0 ? 0 : 1, 1);
    }
    store.setMaskBinData(bits.toByteArray(), 0, 0);
  }

}
