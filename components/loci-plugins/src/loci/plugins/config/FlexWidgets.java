//
// FlexWidgets.java
//

/*
LOCI Plugins for ImageJ: a collection of ImageJ plugins including the
Bio-Formats Importer, Bio-Formats Exporter, Bio-Formats Macro Extensions,
Data Browser and Stack Slicer. Copyright (C) 2005-@year@ Melissa Linkert,
Curtis Rueden and Christopher Peterson.

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

package loci.plugins.config;

import ij.Prefs;

import java.awt.Component;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.services.LuraWaveService;
import loci.formats.services.LuraWaveServiceImpl;

/**
 * Custom widgets for configuring Bio-Formats Flex support.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/loci-plugins/src/loci/plugins/config/FlexWidgets.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/loci-plugins/src/loci/plugins/config/FlexWidgets.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class FlexWidgets implements DocumentListener, IFormatWidgets {

  // -- Fields --

  private String[] labels;
  private Component[] widgets;

  private JTextField licenseBox;

  // -- Constructor --

  public FlexWidgets() {
    LuraWaveService service;
    try {
      ServiceFactory factory = new ServiceFactory();
      service = factory.getInstance(LuraWaveService.class);
    }
    catch (DependencyException e) {
      throw new RuntimeException(e);
    }

    // get license code from ImageJ preferences
    String prefCode = Prefs.get(LuraWaveServiceImpl.LICENSE_PROPERTY, null);
    String propCode = service.getLicenseCode();
    String code = "";
    if (prefCode != null) code = prefCode;
    else if (propCode != null) code = null; // hidden code

    String licenseLabel = "LuraWave license code";
    licenseBox = ConfigWindow.makeTextField();
    licenseBox.setText(code == null ? "(Licensed)" : code);
    licenseBox.setEditable(code != null);
    licenseBox.getDocument().addDocumentListener(this);

    labels = new String[] {licenseLabel};
    widgets = new Component[] {licenseBox};
  }

  // -- DocumentListener API methods --

  public void changedUpdate(DocumentEvent e) {
    documentUpdate();
  }
  public void removeUpdate(DocumentEvent e) {
    documentUpdate();
  }
  public void insertUpdate(DocumentEvent e) {
    documentUpdate();
  }

  // -- IFormatWidgets API methods --

  public String[] getLabels() {
    return labels;
  }

  public Component[] getWidgets() {
    return widgets;
  }

  // -- Helper methods --

  private void documentUpdate() {
    String code = licenseBox.getText();
    Prefs.set(LuraWaveServiceImpl.LICENSE_PROPERTY, code);
  }

}
