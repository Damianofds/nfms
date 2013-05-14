package org.fao.unredd.statsCalculator;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXB;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.fao.unredd.statsCalculator.generated.ClassificationType;
import org.fao.unredd.statsCalculator.generated.Classifications;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.coverage.grid.io.imageio.GeoToolsWriteParams;
import org.geotools.gce.geotiff.GeoTiffFormat;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.raster.AreaGridProcess;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.sun.media.imageio.plugins.tiff.TIFFImageWriteParam;
import com.vividsolutions.jts.geom.Envelope;

/**
 * <p>
 * Calculates the statistics of a GeoServer temporal mosaic. Receives as input
 * the folder where the tiff files are.
 * </p>
 * <p>
 * The contents of the folder that are relevant for this process are:
 * </p>
 * <ul>
 * <li>the tiff files of the mosaic. They contain two values: 0 for non forest,
 * 1 for forest</li>
 * <li>an indexer.properties file indicating how to obtain the timestamp
 * associated to each file.</li>
 * </ul>
 * 
 * @author fergonco
 */
public class StatsCalculator {

	private static final String MOSAIC_SUB_FOLDER = "mosaic";
	private static final String CONFIGURATION_SUB_FOLDER = "configuration";
	private static final int INVALID_ARGS = -1;

	public static void main(String[] args) throws IllegalArgumentException,
			IOException {
		Options options = new Options();
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Folder with the temporal mosaic");
		Option option = OptionBuilder.create("f");
		options.addOption(option);
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e1) {
			printUsage();
			System.exit(INVALID_ARGS);
		}

		// Read folder as unique parameter
		File folder = null;
		if (cmd.hasOption("f")) {
			folder = new File(cmd.getOptionValue("f"));
		} else {
			printUsage();
			System.exit(INVALID_ARGS);
		}

		if (!folder.exists()) {
			System.err.println("The folder does not exist: "
					+ folder.getAbsolutePath());
			System.exit(INVALID_ARGS);
		}

		// Get a hashmap with the association between timestamps and files
		TreeMap<Date, File> files = new TreeMap<Date, File>();
		File mosaicFolder = new File(folder, MOSAIC_SUB_FOLDER);
		if (!mosaicFolder.exists()) {
			System.err.println("The folder does not contain a subfolder "
					+ "'mosaic' containing the coverages: "
					+ folder.getAbsolutePath());
			System.exit(INVALID_ARGS);
		}
		File configurationSubFolder = new File(folder, CONFIGURATION_SUB_FOLDER);
		if (!configurationSubFolder.exists()) {
			System.err.println("The folder does not contain a subfolder "
					+ "'configuration': " + folder.getAbsolutePath());
			System.exit(INVALID_ARGS);
		}
		File[] snapshotFiles = mosaicFolder.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				String lowerCaseName = name.toLowerCase();
				return lowerCaseName.endsWith(".tiff")
						|| lowerCaseName.endsWith(".tif");
			}
		});
		Pattern pattern = Pattern.compile("[0-9]{4}");
		for (File snapshotFile : snapshotFiles) {
			String name = snapshotFile.getName();
			Matcher matcher = pattern.matcher(name);
			if (!matcher.find()) {
				System.err.println("The date of the snapshot "
						+ "could not be obtained: "
						+ snapshotFile.getAbsolutePath());
				System.exit(INVALID_ARGS);
			}
			String dateString = matcher.group();
			try {
				files.put(new SimpleDateFormat("yyyy").parse(dateString),
						snapshotFile);
			} catch (java.text.ParseException e) {
				System.err.println("The date of the snapshot "
						+ "could not be obtained: " + dateString);
				System.exit(INVALID_ARGS);
			}
		}
		if (files.isEmpty()) {
			System.err.println("There are no snapshots in the mosaic folder: "
					+ mosaicFolder.getAbsolutePath());
			System.exit(INVALID_ARGS);
		}

		// Obtain the raster info from first tiff
		Entry<Date, File> firstSnapshot = files.firstEntry();
		File firstSnapshotFile = firstSnapshot.getValue();
		RasterInfo firstSnapshotInfo = new RasterInfo(firstSnapshotFile);

		// Calculate statistics for every snapshot
		Iterator<Date> timestampIterator = files.keySet().iterator();
		while (timestampIterator.hasNext()) {
			Date timestamp = timestampIterator.next();
			File timestampFile = files.get(timestamp);

			// Check the snapshot matches first snapshot's geometry
			if (!new RasterInfo(timestampFile)
					.matchesGeometry(firstSnapshotInfo)) {
				System.err.println("The snapshot of '" + timestamp
						+ "' does not match the "
						+ "geometry of the first snapshot: '"
						+ firstSnapshot.getKey() + "'");
			}

			/*
			 * Remove the area-raster if it does not match the snapshots
			 * geometry
			 */
			File areaRaster = new File(configurationSubFolder,
					"sample-areas.tiff");
			if (areaRaster.exists()) {
				if (!firstSnapshotInfo.matchesGeometry(new RasterInfo(
						areaRaster))) {
					if (!areaRaster.delete()) {
						System.err.println("The area raster geometry "
								+ "does not match "
								+ "the snapshots' and could not "
								+ "be deleted to be regenerated");
						System.exit(-2);
					}
				}
			}

			// Generate the area-raster if it does not exist
			if (!areaRaster.exists()) {
				AreaGridProcess process = new AreaGridProcess();
				GeneralEnvelope generalEnvelope = firstSnapshotInfo
						.getEnvelope();
				Envelope jtsEnvelope = new Envelope(
						generalEnvelope.getMinimum(0),
						generalEnvelope.getMaximum(0),
						generalEnvelope.getMinimum(1),
						generalEnvelope.getMaximum(1));
				ReferencedEnvelope envelope = new ReferencedEnvelope(
						jtsEnvelope, firstSnapshotInfo.getCRS());
				int width = firstSnapshotInfo.getWidth();
				int height = firstSnapshotInfo.getHeight();
				GridCoverage2D grid = process.execute(envelope, width, height);
				GeoTiffWriter writer = null;
				try {
					writer = new GeoTiffWriter(areaRaster);
				} catch (IOException e) {
					System.err.println("Cannot create the writer for file: "
							+ areaRaster.getAbsolutePath());
					System.exit(-3);
				}
				try {
					GeoTiffWriteParams params = new GeoTiffWriteParams();
					params.setTilingMode(TIFFImageWriteParam.MODE_EXPLICIT);
					params.setTiling(512, 512);
					ParameterValue<GeoToolsWriteParams> value = GeoTiffFormat.GEOTOOLS_WRITE_PARAMS
							.createValue();
					value.setValue(params);
					writer.write(grid, new GeneralParameterValue[] { value });
				} catch (RuntimeException e) {
					throw new RuntimeException("Bug writing the area raster");
				} catch (IOException e) {
					throw new RuntimeException("Error writing the area raster");
				} finally {
					writer.dispose();
				}
			}

			// Read the calculations
			File classificationsFile = new File(configurationSubFolder,
					"classifications.xml");
			List<ClassificationType> classifications = JAXB.unmarshal(
					classificationsFile, Classifications.class)
					.getClassification();
			for (ClassificationType classificationType : classifications) {
				// TODO Execute the calculation method
				System.out.println("Executing the stats for \"" + timestamp
						+ "\" (" + timestampFile + ") with "
						+ classificationType.getLayer() + "/"
						+ classificationType.getFieldName()
						+ " as classification ");
			}
		}
	}

	private static void printUsage() {
		throw new UnsupportedOperationException("Print usage not supported yet");
	}

	private static class RasterInfo {

		private GeneralEnvelope envelope;
		private GridEnvelope gridRange;
		private CoordinateReferenceSystem crs;

		public RasterInfo(File raster) throws IllegalArgumentException,
				IOException {
			AbstractGridFormat format = GridFormatFinder.findFormat(raster);
			AbstractGridCoverage2DReader reader = format.getReader(raster);
			envelope = reader.getOriginalEnvelope();
			gridRange = reader.getOriginalGridRange();
			crs = reader.getCrs();
		}

		public int getHeight() {
			return gridRange.getSpan(1);
		}

		public int getWidth() {
			return gridRange.getSpan(0);
		}

		public GeneralEnvelope getEnvelope() {
			return envelope;
		}

		public CoordinateReferenceSystem getCRS() {
			return crs;
		}

		public boolean matchesGeometry(RasterInfo that) {
			return this.getCRS().toWKT().equals(that.getCRS().toWKT())
					&& this.getEnvelope().equals(that.getEnvelope(), 0.000001,
							false)//
					&& this.getWidth() == that.getWidth()
					&& this.getHeight() == that.getHeight();
		}
	}
}
