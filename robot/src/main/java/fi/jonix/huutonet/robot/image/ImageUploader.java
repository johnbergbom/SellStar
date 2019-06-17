package fi.jonix.huutonet.robot.image;

import fi.jonix.huutonet.domain.model.Image;

public interface ImageUploader {
	
	public boolean uploadImage(Image image) throws Exception;
	public boolean isImageAvailable(Image image) throws Exception;
	
}
