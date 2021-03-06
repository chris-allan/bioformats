/*
 * ome.xml.model.enums.FontStyle
 *
 *-----------------------------------------------------------------------------
 *
 *  Copyright (C) 2007 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee,
 *      University of Wisconsin-Madison
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *-----------------------------------------------------------------------------
 */

/*-----------------------------------------------------------------------------
 *
 * THIS IS AUTOMATICALLY GENERATED CODE.  DO NOT MODIFY.
 * Created by callan via xsd-fu on 2010-06-11 17:17:29+0100
 *
 *-----------------------------------------------------------------------------
 */

package ome.xml.model.enums;

public enum FontStyle implements Enumeration
{
  BOLD("Bold"), BOLDITALIC("BoldItalic"), ITALIC("Italic"), REGULAR("Regular");
  
  private FontStyle(String value)
  {
    this.value = value;
  }

  public static FontStyle fromString(String value)
    throws EnumerationException
  {
    if ("Bold".equals(value))
    {
      return BOLD;
    }
    if ("BoldItalic".equals(value))
    {
      return BOLDITALIC;
    }
    if ("Italic".equals(value))
    {
      return ITALIC;
    }
    if ("Regular".equals(value))
    {
      return REGULAR;
    }
    String s = String.format("%s not a supported value of %s",
                             value, FontStyle.class);
    throw new EnumerationException(s);
  }

  public String getValue()
  {
    return value;
  }

  @Override
  public String toString()
  {
    return value;
  }

  private final String value;
}
