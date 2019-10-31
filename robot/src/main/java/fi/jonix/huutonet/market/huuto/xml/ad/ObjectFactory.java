//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2012.03.30 at 01:52:02 AM EEST 
//


package fi.jonix.huutonet.market.huuto.xml.ad;

import java.math.BigDecimal;
import java.math.BigInteger;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the fi.jonix.huutonet.market.huuto.xml.ad package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _Title_QNAME = new QName("http://www.w3.org/2005/Atom", "title");
    private final static QName _VerificationRequired_QNAME = new QName("http://dev.huuto.net/somt", "verificationRequired");
    private final static QName _Id_QNAME = new QName("http://www.w3.org/2005/Atom", "id");
    private final static QName _Name_QNAME = new QName("http://www.w3.org/2005/Atom", "name");
    private final static QName _Updated_QNAME = new QName("http://www.w3.org/2005/Atom", "updated");
    private final static QName _TaxPercentage_QNAME = new QName("http://somt.sanoma.fi/somt", "taxPercentage");
    private final static QName _Quantity_QNAME = new QName("http://somt.sanoma.fi/somt", "quantity");
    private final static QName _ExpirationTime_QNAME = new QName("http://somt.sanoma.fi/somt", "expirationTime");
    private final static QName _Description_QNAME = new QName("http://somt.sanoma.fi/somt", "description");
    private final static QName _Summary_QNAME = new QName("http://www.w3.org/2005/Atom", "summary");
    private final static QName _City_QNAME = new QName("http://somt.sanoma.fi/somt", "city");
    private final static QName _ItemStatus_QNAME = new QName("http://somt.sanoma.fi/somt", "itemStatus");
    private final static QName _Uri_QNAME = new QName("http://www.w3.org/2005/Atom", "uri");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: fi.jonix.huutonet.market.huuto.xml.ad
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Entry }
     * 
     */
    public Entry createEntry() {
        return new Entry();
    }

    /**
     * Create an instance of {@link BuyerRequirements }
     * 
     */
    public BuyerRequirements createBuyerRequirements() {
        return new BuyerRequirements();
    }

    /**
     * Create an instance of {@link Content }
     * 
     */
    public Content createContent() {
        return new Content();
    }

    /**
     * Create an instance of {@link PaymentMethods }
     * 
     */
    public PaymentMethods createPaymentMethods() {
        return new PaymentMethods();
    }

    /**
     * Create an instance of {@link Category }
     * 
     */
    public Category createCategory() {
        return new Category();
    }

    /**
     * Create an instance of {@link MinimumIncrease }
     * 
     */
    public MinimumIncrease createMinimumIncrease() {
        return new MinimumIncrease();
    }

    /**
     * Create an instance of {@link StartingPrice }
     * 
     */
    public StartingPrice createStartingPrice() {
        return new StartingPrice();
    }

    /**
     * Create an instance of {@link Price }
     * 
     */
    public Price createPrice() {
        return new Price();
    }

    /**
     * Create an instance of {@link DeliveryMethod }
     * 
     */
    public DeliveryMethod createDeliveryMethod() {
        return new DeliveryMethod();
    }

    /**
     * Create an instance of {@link CurrentPrice }
     * 
     */
    public CurrentPrice createCurrentPrice() {
        return new CurrentPrice();
    }

    /**
     * Create an instance of {@link PaymentMethod }
     * 
     */
    public PaymentMethod createPaymentMethod() {
        return new PaymentMethod();
    }

    /**
     * Create an instance of {@link Author }
     * 
     */
    public Author createAuthor() {
        return new Author();
    }

    /**
     * Create an instance of {@link Location }
     * 
     */
    public Location createLocation() {
        return new Location();
    }

    /**
     * Create an instance of {@link DeliveryMethods }
     * 
     */
    public DeliveryMethods createDeliveryMethods() {
        return new DeliveryMethods();
    }

    /**
     * Create an instance of {@link Link }
     * 
     */
    public Link createLink() {
        return new Link();
    }

    /**
     * Create an instance of {@link Condition }
     * 
     */
    public Condition createCondition() {
        return new Condition();
    }

    /**
     * Create an instance of {@link Intention }
     * 
     */
    public Intention createIntention() {
        return new Intention();
    }

    /**
     * Create an instance of {@link BuyNowPrice }
     * 
     */
    public BuyNowPrice createBuyNowPrice() {
        return new BuyNowPrice();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.w3.org/2005/Atom", name = "title")
    public JAXBElement<String> createTitle(String value) {
        return new JAXBElement<String>(_Title_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://dev.huuto.net/somt", name = "verificationRequired")
    public JAXBElement<Boolean> createVerificationRequired(Boolean value) {
        return new JAXBElement<Boolean>(_VerificationRequired_QNAME, Boolean.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.w3.org/2005/Atom", name = "id")
    public JAXBElement<String> createId(String value) {
        return new JAXBElement<String>(_Id_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.w3.org/2005/Atom", name = "name")
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    public JAXBElement<String> createName(String value) {
        return new JAXBElement<String>(_Name_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.w3.org/2005/Atom", name = "updated")
    public JAXBElement<XMLGregorianCalendar> createUpdated(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_Updated_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BigDecimal }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://somt.sanoma.fi/somt", name = "taxPercentage")
    public JAXBElement<BigDecimal> createTaxPercentage(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_TaxPercentage_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BigInteger }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://somt.sanoma.fi/somt", name = "quantity")
    public JAXBElement<BigInteger> createQuantity(BigInteger value) {
        return new JAXBElement<BigInteger>(_Quantity_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://somt.sanoma.fi/somt", name = "expirationTime")
    public JAXBElement<XMLGregorianCalendar> createExpirationTime(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_ExpirationTime_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://somt.sanoma.fi/somt", name = "description")
    public JAXBElement<String> createDescription(String value) {
        return new JAXBElement<String>(_Description_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.w3.org/2005/Atom", name = "summary")
    public JAXBElement<String> createSummary(String value) {
        return new JAXBElement<String>(_Summary_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://somt.sanoma.fi/somt", name = "city")
    public JAXBElement<String> createCity(String value) {
        return new JAXBElement<String>(_City_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://somt.sanoma.fi/somt", name = "itemStatus")
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    public JAXBElement<String> createItemStatus(String value) {
        return new JAXBElement<String>(_ItemStatus_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://www.w3.org/2005/Atom", name = "uri")
    public JAXBElement<String> createUri(String value) {
        return new JAXBElement<String>(_Uri_QNAME, String.class, null, value);
    }

}