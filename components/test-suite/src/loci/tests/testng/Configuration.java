//
// Configuration.java
//

/*
LOCI software automated test suite for TestNG. Copyright (C) 2007-@year@
Melissa Linkert and Curtis Rueden. All rights reserved.

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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import loci.common.IniList;
import loci.common.IniParser;
import loci.common.IniTable;
import loci.common.IniWriter;
import loci.common.Location;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.ReaderWrapper;
import loci.formats.meta.IMetadata;

import ome.xml.model.primitives.PositiveInteger;

/**
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/test-suite/src/loci/tests/testng/Configuration.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/test-suite/src/loci/tests/testng/Configuration.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class Configuration {

  // -- Constants --

  public static final int TILE_SIZE = 512;

  private static final String ACCESS_TIME = "access_ms";
  private static final String MEMORY = "mem_mb";
  private static final String TEST = "test";
  private static final String READER = "reader";
  private static final String SERIES = " series_";

  private static final String SIZE_X = "SizeX";
  private static final String SIZE_Y = "SizeY";
  private static final String SIZE_Z = "SizeZ";
  private static final String SIZE_C = "SizeC";
  private static final String SIZE_T = "SizeT";
  private static final String DIMENSION_ORDER = "DimensionOrder";
  private static final String IS_INDEXED = "Indexed";
  private static final String IS_INTERLEAVED = "Interleaved";
  private static final String IS_FALSE_COLOR = "FalseColor";
  private static final String IS_RGB = "RGB";
  private static final String THUMB_SIZE_X = "ThumbSizeX";
  private static final String THUMB_SIZE_Y = "ThumbSizeY";
  private static final String PIXEL_TYPE = "PixelType";
  private static final String IS_LITTLE_ENDIAN = "LittleEndian";
  private static final String MD5 = "MD5";
  private static final String ALTERNATE_MD5 = "Alternate_MD5";
  private static final String TILE_MD5 = "Tile_MD5";
  private static final String TILE_ALTERNATE_MD5 = "Tile_Alternate_MD5";
  private static final String PHYSICAL_SIZE_X = "PhysicalSizeX";
  private static final String PHYSICAL_SIZE_Y = "PhysicalSizeY";
  private static final String PHYSICAL_SIZE_Z = "PhysicalSizeZ";
  private static final String TIME_INCREMENT = "TimeIncrement";
  private static final String LIGHT_SOURCE = "LightSource_";
  private static final String CHANNEL_NAME = "ChannelName_";
  private static final String EMISSION_WAVELENGTH = "EmissionWavelength_";
  private static final String EXCITATION_WAVELENGTH = "ExcitationWavelength_";
  private static final String DETECTOR = "Detector_";
  private static final String NAME = "Name";
  private static final String SERIES_COUNT = "series_count";
  private static final String CHANNEL_COUNT = "channel_count";

  // -- Fields --

  private String dataFile;
  private String configFile;
  private IniList ini;

  private IniTable currentTable;
  private IniTable globalTable;

  // -- Constructors --

  public Configuration(String dataFile, String configFile) throws IOException {
    this.dataFile = dataFile;
    this.configFile = configFile;

    BufferedReader reader = new BufferedReader(new FileReader(this.configFile));
    IniParser parser = new IniParser();
    parser.setCommentDelimiter(null);
    ini = parser.parseINI(reader);
    pruneINI();
  }

  public Configuration(IFormatReader reader, String configFile) {
    this.dataFile = reader.getCurrentFile();
    this.configFile = configFile;
    populateINI(reader);
  }

  // -- Configuration API methods --

  // -- Global metadata --

  public String getFile() {
    return dataFile;
  }

  public long getAccessTimeMillis() {
    String millis = globalTable.get(ACCESS_TIME);
    if (millis == null) return -1;
    return Long.parseLong(millis);
  }

  public int getMemory() {
    String memory = globalTable.get(MEMORY);
    if (memory == null) return -1;
    return Integer.parseInt(memory);
  }

  public boolean doTest() {
    return new Boolean(globalTable.get(TEST)).booleanValue();
  }

  public String getReader() {
    return globalTable.get(READER);
  }

  public int getSeriesCount() {
    return Integer.parseInt(globalTable.get(SERIES_COUNT));
  }

  // -- Per-series metadata --

  public int getSizeX() {
    return Integer.parseInt(currentTable.get(SIZE_X));
  }

  public int getSizeY() {
    return Integer.parseInt(currentTable.get(SIZE_Y));
  }

  public int getSizeZ() {
    return Integer.parseInt(currentTable.get(SIZE_Z));
  }

  public int getSizeC() {
    return Integer.parseInt(currentTable.get(SIZE_C));
  }

  public int getSizeT() {
    return Integer.parseInt(currentTable.get(SIZE_T));
  }

  public String getDimensionOrder() {
    return currentTable.get(DIMENSION_ORDER);
  }

  public boolean isInterleaved() {
    return new Boolean(currentTable.get(IS_INTERLEAVED)).booleanValue();
  }

  public boolean isIndexed() {
    return new Boolean(currentTable.get(IS_INDEXED)).booleanValue();
  }

  public boolean isFalseColor() {
    return new Boolean(currentTable.get(IS_FALSE_COLOR)).booleanValue();
  }

  public boolean isRGB() {
    return new Boolean(currentTable.get(IS_RGB)).booleanValue();
  }

  public int getThumbSizeX() {
    return Integer.parseInt(currentTable.get(THUMB_SIZE_X));
  }

  public int getThumbSizeY() {
    return Integer.parseInt(currentTable.get(THUMB_SIZE_Y));
  }

  public String getPixelType() {
    return currentTable.get(PIXEL_TYPE);
  }

  public boolean isLittleEndian() {
    return new Boolean(currentTable.get(IS_LITTLE_ENDIAN)).booleanValue();
  }

  public String getMD5() {
    return currentTable.get(MD5);
  }

  public String getAlternateMD5() {
    return currentTable.get(ALTERNATE_MD5);
  }

  public String getTileMD5() {
    return currentTable.get(TILE_MD5);
  }

  public String getTileAlternateMD5() {
    return currentTable.get(TILE_ALTERNATE_MD5);
  }

  public Double getPhysicalSizeX() {
    String physicalSize = currentTable.get(PHYSICAL_SIZE_X);
    return physicalSize == null ? null : new Double(physicalSize);
  }

  public Double getPhysicalSizeY() {
    String physicalSize = currentTable.get(PHYSICAL_SIZE_Y);
    return physicalSize == null ? null : new Double(physicalSize);
  }

  public Double getPhysicalSizeZ() {
    String physicalSize = currentTable.get(PHYSICAL_SIZE_Z);
    return physicalSize == null ? null : new Double(physicalSize);
  }

  public Double getTimeIncrement() {
    String physicalSize = currentTable.get(TIME_INCREMENT);
    return physicalSize == null ? null : new Double(physicalSize);
  }

  public int getChannelCount() {
    return Integer.parseInt(currentTable.get(CHANNEL_COUNT));
  }

  public String getLightSource(int channel) {
    return currentTable.get(LIGHT_SOURCE + channel);
  }

  public String getChannelName(int channel) {
    return currentTable.get(CHANNEL_NAME + channel);
  }

  public Integer getEmissionWavelength(int channel) {
    String wavelength = currentTable.get(EMISSION_WAVELENGTH + channel);
    return wavelength == null ? null : new Integer(wavelength);
  }

  public Integer getExcitationWavelength(int channel) {
    String wavelength = currentTable.get(EXCITATION_WAVELENGTH + channel);
    return wavelength == null ? null : new Integer(wavelength);
  }

  public String getDetector(int channel) {
    return currentTable.get(DETECTOR + channel);
  }

  public String getImageName() {
    return currentTable.get(NAME);
  }

  public void setSeries(int series) {
    Location file = new Location(dataFile);
    currentTable = ini.getTable(file.getName() + SERIES + series);
  }

  public void saveToFile() throws IOException {
    IniWriter writer = new IniWriter();
    writer.saveINI(ini, configFile);
  }

  public IniList getINI() {
    return ini;
  }

  // -- Object API methods --

  public boolean equals(Object o) {
    if (!(o instanceof Configuration)) return false;

    Configuration thatConfig = (Configuration) o;
    return this.getINI().equals(thatConfig.getINI());
  }

  // -- Helper methods --

  private void populateINI(IFormatReader reader) {
    IMetadata retrieve = (IMetadata) reader.getMetadataStore();

    ini = new IniList();

    IniTable globalTable = new IniTable();
    putTableName(globalTable, reader, " global");

    int seriesCount = reader.getSeriesCount();

    globalTable.put(SERIES_COUNT, String.valueOf(seriesCount));

    IFormatReader r = reader;
    if (r instanceof ImageReader) {
      r = ((ImageReader) r).getReader();
    }
    else if (r instanceof ReaderWrapper) {
      try {
        r = ((ReaderWrapper) r).unwrap();
      }
      catch (FormatException e) { }
      catch (IOException e) { }
    }

    globalTable.put(READER, TestTools.shortClassName(r));
    globalTable.put(TEST, "true");
    globalTable.put(MEMORY, String.valueOf(TestTools.getUsedMemory()));

    int planeSize = FormatTools.getPlaneSize(reader);
    boolean canOpenImages =
      planeSize > 0 && TestTools.canFitInMemory(planeSize);

    long t0 = System.currentTimeMillis();
    if (canOpenImages) {
      try {
        reader.openBytes(0);
      }
      catch (FormatException e) { }
      catch (IOException e) { }
    }
    long t1 = System.currentTimeMillis();

    globalTable.put(ACCESS_TIME, String.valueOf(t1 - t0));

    ini.add(globalTable);

    for (int series=0; series<seriesCount; series++) {
      reader.setSeries(series);

      IniTable seriesTable = new IniTable();
      putTableName(seriesTable, reader, SERIES + series);

      seriesTable.put(SIZE_X, String.valueOf(reader.getSizeX()));
      seriesTable.put(SIZE_Y, String.valueOf(reader.getSizeY()));
      seriesTable.put(SIZE_Z, String.valueOf(reader.getSizeZ()));
      seriesTable.put(SIZE_C, String.valueOf(reader.getSizeC()));
      seriesTable.put(SIZE_T, String.valueOf(reader.getSizeT()));
      seriesTable.put(DIMENSION_ORDER, reader.getDimensionOrder());
      seriesTable.put(IS_INTERLEAVED, String.valueOf(reader.isInterleaved()));
      seriesTable.put(IS_INDEXED, String.valueOf(reader.isIndexed()));
      seriesTable.put(IS_FALSE_COLOR, String.valueOf(reader.isFalseColor()));
      seriesTable.put(IS_RGB, String.valueOf(reader.isRGB()));
      seriesTable.put(THUMB_SIZE_X, String.valueOf(reader.getThumbSizeX()));
      seriesTable.put(THUMB_SIZE_Y, String.valueOf(reader.getThumbSizeY()));
      seriesTable.put(PIXEL_TYPE,
        FormatTools.getPixelTypeString(reader.getPixelType()));
      seriesTable.put(IS_LITTLE_ENDIAN,
        String.valueOf(reader.isLittleEndian()));

      seriesTable.put(CHANNEL_COUNT,
        String.valueOf(retrieve.getChannelCount(series)));

      planeSize = FormatTools.getPlaneSize(reader);
      canOpenImages = planeSize > 0 && TestTools.canFitInMemory(planeSize);

      if (canOpenImages) {
        try {
          byte[] plane = reader.openBytes(0);
          seriesTable.put(MD5, TestTools.md5(plane));
        }
        catch (FormatException e) {
          // TODO
        }
        catch (IOException e) {
          // TODO
        }
      }

      try {
        int w = (int) Math.min(TILE_SIZE, reader.getSizeX());
        int h = (int) Math.min(TILE_SIZE, reader.getSizeY());

        byte[] tile = reader.openBytes(0, 0, 0, w, h);
        seriesTable.put(TILE_MD5, TestTools.md5(tile));
      }
      catch (FormatException e) {
        // TODO
      }
      catch (IOException e) {
        // TODO
      }

      seriesTable.put(NAME, retrieve.getImageName(series));

      Double physicalX = retrieve.getPixelsPhysicalSizeX(series);
      if (physicalX != null) {
        seriesTable.put(PHYSICAL_SIZE_X, physicalX.toString());
      }
      Double physicalY = retrieve.getPixelsPhysicalSizeY(series);
      if (physicalY != null) {
        seriesTable.put(PHYSICAL_SIZE_Y, physicalY.toString());
      }
      Double physicalZ = retrieve.getPixelsPhysicalSizeZ(series);
      if (physicalZ != null) {
        seriesTable.put(PHYSICAL_SIZE_Z, physicalZ.toString());
      }
      Double timeIncrement = retrieve.getPixelsTimeIncrement(series);
      if (timeIncrement != null) {
        seriesTable.put(TIME_INCREMENT, timeIncrement.toString());
      }

      for (int c=0; c<retrieve.getChannelCount(series); c++) {
        seriesTable.put(CHANNEL_NAME + c, retrieve.getChannelName(series, c));
        try {
          seriesTable.put(LIGHT_SOURCE + c,
            retrieve.getChannelLightSourceSettingsID(series, c));
        }
        catch (NullPointerException e) { }

        PositiveInteger emWavelength =
          retrieve.getChannelEmissionWavelength(series, c);
        if (emWavelength != null) {
          seriesTable.put(EMISSION_WAVELENGTH + c, emWavelength.toString());
        }
        PositiveInteger exWavelength =
          retrieve.getChannelExcitationWavelength(series, c);
        if (exWavelength != null) {
          seriesTable.put(EXCITATION_WAVELENGTH + c, exWavelength.toString());
        }
        try {
          seriesTable.put(DETECTOR + c,
            retrieve.getDetectorSettingsID(series, c));
        }
        catch (NullPointerException e) { }
      }

      ini.add(seriesTable);
    }

  }

  private void putTableName(IniTable table, IFormatReader reader, String suffix)
  {
    Location file = new Location(reader.getCurrentFile());
    table.put(IniTable.HEADER_KEY, file.getName() + suffix);
  }

  private void pruneINI() {
    IniList newIni = new IniList();
    for (IniTable table : ini) {
      String tableName = table.get(IniTable.HEADER_KEY);
      Location file = new Location(dataFile);
      if (tableName.startsWith(file.getName())) {
        newIni.add(table);

        if (tableName.endsWith("global")) {
          globalTable = table;
        }
      }
    }
    ini = newIni;
  }

}
