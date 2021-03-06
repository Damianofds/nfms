/**
 * nfms4redd Portal Interface - http://nfms4redd.org/
 *
 * (C) 2012, FAO Forestry Department (http://www.fao.org/forestry/)
 *
 * This application is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package org.fao.unredd;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.fao.unredd.layers.DataLocator;
import org.fao.unredd.layers.FileLocation;
import org.fao.unredd.layers.Location;
import org.fao.unredd.layers.PasswordGetter;
import org.fao.unredd.layers.folder.MosaicLayerFolder;
import org.fao.unredd.statsCalculator.MixedRasterGeometryException;
import org.fao.unredd.statsCalculator.MosaicProcessor;
import org.fao.unredd.statsCalculator.OutputBuilder;
import org.fao.unredd.statsCalculator.StatsIndicatorConstants;
import org.geotools.gce.imagemosaic.properties.time.TimeParser;
import org.junit.Test;

public class MosaicProcessorTest {

	private static final String LAYER_NAME = "workspace:layer";

	@Test
	public void testCorruptedTiff() throws Exception {
		File temporalMosaic = new File("src/test/resources/data/corruptedTiff");
		MosaicLayerFolder mosaic = new MosaicLayerFolder(LAYER_NAME,
				temporalMosaic);
		DataLocator dataLocator = mock(DataLocator.class);
		Location mockLocation = new FileLocation(temporalMosaic);
		when(dataLocator.locate(mosaic)).thenReturn(mockLocation);
		MosaicProcessor mosaicProcessor = new MosaicProcessor(dataLocator,
				mock(OutputBuilder.class), mosaic);
		try {
			mosaicProcessor.process(null, mock(PasswordGetter.class));
			fail();
		} catch (IOException e) {
		}
	}

	@Test
	public void testSnapshotDifferentGeometry() throws Exception {
		File temporalMosaic = new File(
				"src/test/resources/data/snapshotDifferentGeometry");
		MosaicLayerFolder mosaicLayer = new MosaicLayerFolder(LAYER_NAME,
				temporalMosaic);
		DataLocator dataLocator = mock(DataLocator.class);
		Location mockLocation = new FileLocation(temporalMosaic);
		when(dataLocator.locate(mosaicLayer)).thenReturn(mockLocation);
		MosaicProcessor mosaicProcessor = new MosaicProcessor(dataLocator,
				mock(OutputBuilder.class), mosaicLayer);
		try {
			mosaicProcessor.process(null, mock(PasswordGetter.class));
			fail();
		} catch (MixedRasterGeometryException e) {
		} finally {
			FileUtils.deleteDirectory(mosaicLayer.getWorkFolder());
		}
	}

	@Test
	public void testOkZonesSHP() throws Exception {
		File temporalMosaic = new File("src/test/resources/data/temporalMosaic");
		MosaicLayerFolder mosaicLayer = new MosaicLayerFolder(LAYER_NAME,
				temporalMosaic);
		OutputBuilder outputBuilder = mock(OutputBuilder.class);
		DataLocator dataLocator = mock(DataLocator.class);
		Location mosaicLocation = new FileLocation(temporalMosaic);
		when(dataLocator.locate(mosaicLayer)).thenReturn(mosaicLocation);
		MosaicProcessor mosaicProcessor = new MosaicProcessor(dataLocator,
				outputBuilder, mosaicLayer);
		Location zones = new FileLocation(new File(
				"src/test/resources/okZonesSHP/data/zones.shp"));
		PasswordGetter passwordGetter = mock(PasswordGetter.class);

		mosaicProcessor.process(zones, passwordGetter);

		try {
			File areaRaster = new MosaicLayerFolder(LAYER_NAME, temporalMosaic)
					.getWorkFile(StatsIndicatorConstants.SAMPLE_AREAS_FILE_NAME);
			TimeParser timeParser = new TimeParser();
			verify(outputBuilder).addToOutput(areaRaster,
					timeParser.parse("2000").get(0),
					new File(temporalMosaic, "snapshot_2000.tiff"), zones, 5,
					5, passwordGetter);
			verify(outputBuilder).addToOutput(areaRaster,
					timeParser.parse("2001").get(0),
					new File(temporalMosaic, "snapshot_2001.tiff"), zones, 5,
					5, passwordGetter);
		} finally {
			File workFolder = mosaicLayer.getWorkFolder();
			if (workFolder.exists()) {
				FileUtils.deleteDirectory(workFolder);
			}
		}
	}
}
