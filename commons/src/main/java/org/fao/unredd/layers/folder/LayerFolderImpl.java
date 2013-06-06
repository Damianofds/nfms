package org.fao.unredd.layers.folder;

import java.io.File;

import org.fao.unredd.layers.Layer;

/**
 * Basic concrete implementation of the {@link Layer} interface based on folders
 * 
 * @author fergonco
 */
public class LayerFolderImpl extends AbstractLayerFolder {

	/**
	 * Builds a new instance
	 * 
	 * @param folder
	 * @throws IllegalArgumentException
	 *             If the folder does not exist
	 * @throws InvalidFolderStructureException
	 *             If the layer does not follow the expected rules
	 */
	public LayerFolderImpl(File folder) throws IllegalArgumentException,
			InvalidFolderStructureException {
		super(folder);
	}

}
