package fi.jonix.huutonet.tools.translation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;

@Component(value = "htmlProductPageCreator")
public class HtmlProductPageCreator {
	
	@Autowired
	ImageDAO imageDAO;
	
	public void createHtmlProductPage(File file, Product product) {
		PrintWriter printWriter = null;
		try {
			printWriter = new PrintWriter(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		StringBuffer st = new StringBuffer();
		st.append("<html>\n<head>\n<title>Product " + product.getName()
				+ "</title></head><body>");
		st.append("<h2>" + product.getName() + "</h2>");
		st.append("<h4>Pack Including</h4>");
		st.append("<ul>");
		for (String containsRow : product.getContents().split("\n")) {
			st.append("<li>" + containsRow + "</li>");
		}
		st.append("</ul>");
		st.append("<h4>Technical Specs</h4>");
		st.append("<ul>");
		for (String specRow : product.getTechnicalSpecs().split("\n")) {
			st.append("<li>" + specRow + "</li>");
		}
		st.append("</ul>");
		List<Image> images = this.imageDAO.getByProduct(product);
		
		for (Image image : images) {
			st.append("<A HREF='" + image.getImageHostUrl()
					+ "' TARGET='_blank'><IMG WIDTH='200' SRC='"
					+ image.getImageHostUrl() + "'></A>");
		}
		st.append("</body></html>");
		printWriter.write(st.toString());
		printWriter.flush();
	}

}
