//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2012.04.05 at 02:08:01 AM EEST 
//


package fi.jonix.huutonet.market.huuto.xml.ad09beta;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element ref="{http://dev.huuto.net/somt}verificationRequired"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "verificationRequired"
})
@XmlRootElement(name = "buyerRequirements")
public class BuyerRequirements {

    @XmlElement(namespace = "http://dev.huuto.net/somt")
    protected boolean verificationRequired;

    /**
     * Gets the value of the verificationRequired property.
     * 
     */
    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    /**
     * Sets the value of the verificationRequired property.
     * 
     */
    public void setVerificationRequired(boolean value) {
        this.verificationRequired = value;
    }

}