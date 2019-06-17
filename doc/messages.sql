-- Mall att använda då man lägger in nya texter:
-- insert into message (id,key,seller_id,language,text) values (nextval('message_id_seq'),'KEY',null,'fi_FI','TEXT');

--------------------------------
-- DEFAULT TEXTS FOR FINNISH: --
--------------------------------

-- accountAndPaymentReferenceInfo
update message set text = 'Handelsbanken\nMaksun saaja: ${SIGNATURE}\nTilinumero: ${ACCOUNT_NUMBER_IBAN} (BIC: ${ACCOUNT_NUMBER_BIC})\nViitenumero: ${REFERENCE_NUMBER} (HUOM! Viitenumero on pakollinen tieto)' where key = 'accountAndPaymentReferenceInfo' and seller_id is null;

-- address
update message set text = 'Osoite' where key = 'address' and seller_id is null;

-- bidWonOutOfStock
update message set text = 'Kävipä nolosti. Ilmoitushetkellä korvamerkitty tuotteemme onkin jo ehtinyt toisen asiakkaan haltuun. Käytännössä halutetessasi ilmoituksen ${AD_HEADLINE} tilausnumerolla ${ORDER_ID} huutamasi tuote käsitellään seuraavasta täydennyserästä. Tilauksen viitenumero on ${REFERENCE_NUMBER}.\n\nTäydennyseriä saapuu jatkuvasti ja niitä odotelleet tilaukset käsitellään pikaisesti. Mikäli täydennyserä saapuu viikon sisällä, niin lähetämme maksuohjeet tilauksestasi. Jos täydennyserän saapumiseen kuluu tätä kauemmin, niin peruutamme tilauksen ja lähetämme tästä vahvistuksen sähköpostitse.' where key = 'bidWonOutOfStock' and seller_id is null;

-- billInfoOnlyForProductsInStock
update message set text = 'Joku tilaus nipussa odottaa täydennyserää ja seuraava lasku koskee VAAN nipun varastolla olevia tilauksia.\n\nLaskun tiedot:' where key = 'billInfoOnlyForProductsInStock' and seller_id is null;

-- bundleInformation
update message set text = 'Postikulujen yhdistäminen:\n--------------------------\nVoit yhdistää postikulut jos ostat useita tuotteita. Ehtoina on:\n1.) Paketit lähetetään samaan osoitteeseen.\n2.) Yhteinen hinta on niin matala, että ALV- ja tullirajat eivät ylity.\n\nHUOM: postikulut yhdistetään automaattisesti. Jos eivät yhdisty, niin tarkista osoitteita joihin\nmielestäsi niputettavat tuotteet lähetetään ja muuta tarvittaessa osoitetiedot.\n\nHUOM: ei voida enää yhdistää tuotteita nippuun kun nippu on kokonaisuudessaan maksettu.\n\nEli voit kaikessa rauhassa ostaa useita tuotteita ja kun olet valmis, niin voit tilata koontilaskun\nja maksa kaiken pois yhdellä tilisiirrolla.' where key = 'bundleInformation' and seller_id is null;

-- bundleShippedByProviderHeaderWithTrackingCode
update message set text = 'Seuraavat tuotteet ovat tänään viety postiin (nippunumerolla ${BUNDLE_ID}):\n${TRACKING_OBJECT_ORDERS}\nLähetyksen seurantakoodi on ${TRACKING_CODE}' where key = 'bundleShippedByProviderHeaderWithTrackingCode' and seller_id is null;

-- bundleShippedFromProviderHeaderWithoutTrackingCode
update message set text = 'Seuraavat tuotteet ovat tänään viety postiin (nippunumerolla ${BUNDLE_ID}):\n${TRACKING_OBJECT_ORDERS}\n' where key = 'bundleShippedFromProviderHeaderWithoutTrackingCode' and seller_id is null;

-- city
update message set text = 'Kaupunki' where key = 'city' and seller_id is null;

-- customerPaidForCanceledOrder
update message set text = 'Moi,\n\nOlemme saaneet maksusuorituksen ${REFUND_SUM} tilauksesta ${ORDER_ID} (${AD_HEADLINE}), joka on jo peruutettu. Mikäli et ole itse perunut tilausta, niin tämä voi johtua siitä, että tilaus oli automaattisesti suljettu koska maksaminen viipyi liian kauan.\n\nVoit käyttää maksamasi summan toisen tuotteen tai tuotteiden tilaamiseen. Mikäli haluat mieluummin saadan hyvityksen rahana, niin seuraavan linkin kautta pääset syöttämään tilinumeron, johon voimme maksaa hyvityksen.\n\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/refund?seller=${SIGNATURE_URL_ENC}&confirmationCode=${CONFIRMATION_CODE}\n\nVarmennuskoodi hyvitystä varten: ${CONFIRMATION_CODE}\n\nOlette oikeutettu hyvitykseen kahden kuukauden ajan tästä tiedotuksesta.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'customerPaidForCanceledOrder' and seller_id is null;

-- customerPaidTooMuch
update message set text = 'Moi,\n\nOlette tehneet hieman liian suuren tilisiirron. Olette oikeutettu ${REFUND_SUM} suuruiseen hyvitykseen.\n\nHyvitys käytetään hyväksenne automaattisesti seuraavan tilauksenne yhteydessä, kun käytätte\nkoontilaskua. Mikäli haluatte hyvityksen rahana, niin teidän tulee täyttää lomake osoitteessa\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/refund?seller=${SIGNATURE_URL_ENC}&confirmationCode=${CONFIRMATION_CODE}\n\nVarmennuskoodi hyvitystä varten: ${CONFIRMATION_CODE}\n\nOlette oikeutettu hyvitykseen kahden kuukauden ajan tästä tiedotuksesta.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'customerPaidTooMuch' and seller_id is null;

-- digitsNotAllowedInField
update message set text = 'Numeroja ei sallittu' where key = 'digitsNotAllowedInField' and seller_id is null;

-- digitsRequiredInField
update message set text = 'Numero puuttuu' where key = 'digitsRequiredInField' and seller_id is null;

-- disallowedValue
update message set text = 'Ei sallittu arvo' where key = 'disallowedValue' and seller_id is null;

-- email
update message set text = 'Sähköposti' where key = 'email' and seller_id is null;

-- faultyContactInfoAtNewOrder
update message set text = 'Kiitos tilauksesta,\n\nTilauksen käsittelyssä tuli virhe. Osoitetiedot ovat virheellisiä.\n\n${FAULTY_CONTACT_INFO_FOR_ORDER_X}\n\nSähköpostiosoite: ${CUSTOMER_EMAIL}\n\nTilauksen käsittely jatkuu sen jälkeen.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'faultyContactInfoAtNewOrder' and seller_id is null;

-- faultyContactInfoForOrderX
update message set text = '${REASON}\n\nTilausnumero: ${ORDER_ID}\nViitenumero: ${REFERENCE_NUMBER}\n${CUSTOMER_FIRST_NAME} ${CUSTOMER_LAST_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE} ${CUSTOMER_CITY}\n\nMikäli olet varma siitä, että osoite on oikein, niin ole hyvä ja ota yhteyttä meihin sähköpostitse.\nMuuten ole hyvä ja korjaa yhteystietosi oheisen linkin kautta:\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/addressChange?seller=${SIGNATURE_URL_ENC}&referenceNumber=${REFERENCE_NUMBER}&firstName=${FIRST_NAME_URL_ENC}&lastName=${LAST_NAME_URL_ENC}&address=${CUSTOMER_ADDRESS_URL_ENC}&postCode=${CUSTOMER_POSTCODE_URL_ENC}&city=${CUSTOMER_CITY_URL_ENC}&emailAddress=${CUSTOMER_EMAIL_URL_ENC}' where key = 'faultyContactInfoForOrderX' and seller_id is null;

-- faultyContactInfoReminder
update message set text = 'Moi,\n\nTilauksesi osoitetiedot ovat edelleen virheelliset. Voisitko ystävällisesti käydä korjaamassa osoitetietosi seuraavan linkin kautta, niin tilauksesi pääsee etenemään: ${GOOGLE_ORDER_URL}${CONTEXT_PATH}/addressChange?seller=${SIGNATURE_URL_ENC}&referenceNumber=${REFERENCE_NUMBER}&firstName=${FIRST_NAME_URL_ENC}&lastName=${LAST_NAME_URL_ENC}&address=${CUSTOMER_ADDRESS_URL_ENC}&postCode=${CUSTOMER_POSTCODE_URL_ENC}&city=${CUSTOMER_CITY_URL_ENC}&emailAddress=${CUSTOMER_EMAIL_URL_ENC}\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'faultyContactInfoReminder' and seller_id is null;

-- faultyMarketSalesIdEmail
update message set text = 'Moi,\n\nHalusit tilata tuotteen tunnisteella ${MARKET_SALES_ID} mutta tämä tunniste\nei löydy meidän tietokannasta. Ole hyvä ja yritä uudelleen.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'faultyMarketSalesIdEmail' and seller_id is null;

-- fieldContainsIllegalCharacter
update message set text = 'Sisältää ei-sallittuja merkkejä' where key = 'fieldContainsIllegalCharacter' and seller_id is null;

-- firstName
update message set text = 'Etunimi' where key = 'firstName' and seller_id is null;

-- followingOrderIsAddedToBundle
update message set text = 'Nippunumeronne on ${ORDER_BUNDLE_ID}. Nipun postikulut on ${SHIPPING_COST}.\n\nNipussa oli jo seuraavat tilaukset:\n${PREVIOUS_ORDERS}\nNippuun on lisätty seuraava tilaus:\n${ADDED_ORDER}' where key = 'followingOrderIsAddedToBundle' and seller_id is null;

-- followingOrdersAreInBundle
update message set text = 'Nippunumeronne on ${ORDER_BUNDLE_ID}. Nipussa on seuraavat tilaukset:\n${BUNDLE_ORDERS}' where key = 'followingOrdersAreInBundle' and seller_id is null;

-- footer
update message set text = 'Ystävällisin terveisin,\n${SIGNATURE}' where key = 'footer' and seller_id is null;

-- generalPaymentInfoHeader
update message set text = 'Tilauksenne käsittely jatkuu, kun maksu on rekisteröitynyt tilillemme.\n\nLaskun tiedot:' where key = 'generalPaymentInfoHeader' and seller_id is null;

-- generalPaymentInfoFooter
update message set text = 'Muistathan käyttää viitenumeroa, koska tilausjärjestelmämme tarkistaa laskut automaattisesti viitenumeron perusteella. Tilausjärjestelmä ilmoittaa sähköpostitse, kun rahat ovat kirjautuneet tilillemme.\n\nMikäli olet tilannut kerralla useamman tuotteen ja haluat maksaa kaiken samalla laskulla, niin se onnistuu seuraavan linkin takaa aukeavalla koontilaskun pyyntölomakkeella: ${GOOGLE_ORDER_URL}${CONTEXT_PATH}/commonBill?seller=${SIGNATURE_URL_ENC}&emailAddress=${CUSTOMER_EMAIL_URL_ENC}' where key = 'generalPaymentInfoFooter' and seller_id is null;

-- generalProblem
update message set text = 'Moi,\n\nTilauksesi ${ORDER_ID} käsittelyssä on ilmennyt ongelmia. Tiedoitamme asiasta enemmän viikon sisällä.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'generalProblem' and seller_id is null;

-- headerHi
update message set text = 'Moi,' where key = 'headerHi' and seller_id is null;

-- headerThanksForOrder
update message set text = 'Kiitos tilauksestanne,' where key = 'headerThanksForOrder' and seller_id is null;

-- lastName
update message set text = 'Sukunimi' where key = 'lastName' and seller_id is null;

-- moneyReceivedEmail
update message set text = 'Moi,\n\nTilausnumerolla ${ORDER_ID} tilaamanne tuotteen ${AD_HEADLINE} maksu ${TOTAL_PRICE} on nyt kirjautunut tilillemme.\n\nTuote lähetetään keskitetyn tilausjärjestelmämme seuraavan lähetyserän mukana eli noin viikon sisällä. Ilmoitamme teille myös sähköpostitse, kun tilaus lähtee postin kuljetettavaksi.\n\nTuote lähetetään seuraavin tiedoin:\n\n${CUSTOMER_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE}\n${CUSTOMER_CITY}\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'moneyReceivedEmail' and seller_id is null;

-- moneyReceivedForBundleEmail
update message set text = 'Moi,\n\nNippunumerolla ${BUNDLE_ID} tilaamanne tilausten maksu ${TOTAL_PRICE} on nyt kirjautunut tilillemme.\n\nTuotteet lähetetään keskitetyn tilausjärjestelmämme seuraavan lähetyserän mukana eli noin viikon sisällä. Ilmoitamme teille myös sähköpostitse, kun nippu lähtee postin kuljetettavaksi.\n\nTuotteet lähetetään seuraavin tiedoin:\n\n${CUSTOMER_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE}\n${CUSTOMER_CITY}\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'moneyReceivedForBundleEmail' and seller_id is null;

-- moneyRefundedFromProvider
update message set text = 'Moi,\n\nSaat tilaamastasi tuotteesta ${AD_HEADLINE} (tilausnumero ${ORDER_ID}) hyvityksen: ${REFUND_SUM}\n\nVoit käyttää maksamasi summan toisen tuotteen tai tuotteiden tilaamiseen. Mikäli haluat mieluummin saadan hyvityksen rahana, niin seuraavan linkin kautta pääset syöttämään tilinumeron, johon voimme maksaa hyvityksen.\n\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/refund?seller=${SIGNATURE_URL_ENC}&confirmationCode=${CONFIRMATION_CODE}\n\nVarmennuskoodi hyvitystä varten: ${CONFIRMATION_CODE}\n\nHUOM: Mikäli sinulla on useita tuotteita hyvitettävinä, niin ei tarvitse jokaiselle erikseen syöttää tilinumeron. Riittää, että syötät tilinumeron kerran.\n\nOlette oikeutettu hyvitykseen kahden kuukauden ajan tästä tiedotuksesta.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'moneyRefundedFromProvider' and seller_id is null;

-- multiOrderBundleShippedByPostOffice
update message set text = 'Moi,\n\nLähetyksesi jonka nippunumero on ${BUNDLE_ID} on nyt lähtenyt kohti Suomea. Ulkomaiden postinseurantapalvelut eivät kuitenkaan tarjoa tietoa Suomen sisällä tapahtuvasta postin lähetyksen etenemisestä. Olemme ilmoittaneet lähetyksesi tiedot www.seuraapostia.fi-palveluun, joka tiedottaa teitä automaattisesti lähetyksen etenemisestä Suomen postiverkossa. Ensimmäisen tiedotteen saat, kun paketti on saapunut Suomeen. Voit tämän jälkeen halutessasi myös itse seurata paketin etenemistä suoraan postin sivuilta: http://www.verkkoposti.com/e3/TrackinternetServlet?lang=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=${TRACKING_CODE}&LOTUS_hae=Hae\n\nNipussa ovat seuraavat tilaukset:\n${BUNDLE_ORDERS}\nLähetyksen seurantakoodi on ${TRACKING_CODE}\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'multiOrderBundleShippedByPostOffice' and seller_id is null;

-- newBatchNotReceivedClosing
update message set text = 'Moi,\n\nTäydennyserä ei tullut viikon sisällä (tilausnumero ${ORDER_ID}). Tilaus suljetaan. Pahoittelemme tapahtunutta.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'newBatchNotReceivedClosing' and seller_id is null;

-- newBatchReceived
update message set text = 'Tuotteen ${AD_HEADLINE} täydennyserä tuli (tilausnumero ${ORDER_ID})!' where key = 'newBatchReceived' and seller_id is null;

-- newBatchReceivedForMultiOrderBundle
update message set text = 'Tilausnumero ${ORDER_ID}: Tuotteen ${AD_HEADLINE} täydennyserä tuli!' where key = 'newBatchReceivedForMultiOrderBundle' and seller_id is null;

-- noLongerForSale
update message set text = 'Moi,\n\nHalusit tilata tuotteen tunnisteella ${MARKET_SALES_ID} (${AD_HEADLINE}) mutta tämä tuote ei ole enää tuotevalikoimassamme.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'noLongerForSale' and seller_id is null;

-- noLongerForSalePurchaseFromMarket
update message set text = 'Moi,\n\nHalusit tilata tuotteen tunnisteella ${MARKET_SALES_ID} (${AD_HEADLINE}) mutta tämä tuote ei ole valitettavasti enää saatavilla. Tuote oli siis vielä saatavilla silloin kun myynti-ilmoitus tehtiin, mutta ilmoituksen aukioloaikana tuote oli myyty loppuun (tulee paljon tilauksia koko ajan eri kanavien kautta). Näin ollen tämä tuote on poistunut meidän tuotevalikoimasta. Hyvin harvoin käy näin. Pahoittelemme suuresti.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'noLongerForSalePurchaseFromMarket' and seller_id is null;

-- notYetReachedDestinationCountry
update message set text = 'Moi,\n\nTilauksesi ${ORDER_ID} on lähtenyt postin mukana kohti Suomea (${DATE}), mutta ei ole vielä kirjautunut Suomen postin järjestelmään. Selvitämme asiaa. Tiedoitamme lisää viikon sisällä.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'notYetReachedDestinationCountry' and seller_id is null;

-- oneOrderNewBatchCameSimple
update message set text = 'Tilausnumero: ${ORDER_ID}, hinta: ${FINAL_SALES_PRICE}, maksettu: ${ALREADY_PAID}, status: varastolla, tuote: ${AD_HEADLINE}' where key = 'oneOrderNewBatchCameSimple' and seller_id is null;

-- oneOrderNewBatchCameSimpleWithVat
update message set text = 'Tilausnumero: ${ORDER_ID}, hinta: ${PRODUCT_FINAL_SALES_PRICE_INCL_VAT} (sis. alv ${ALV_PERCENTAGE} %, eli alvin osuus on ${PRODUCT_VAT_AMOUNT}), maksettu: ${ALREADY_PAID}, status: varastolla, tuote: ${AD_HEADLINE}' where key = 'oneOrderNewBatchCameSimpleWithVat' and seller_id is null;

-- oneOrderOutOfStockSimple
update message set text = 'Tilausnumero: ${ORDER_ID}, hinta: ${FINAL_SALES_PRICE}, maksettu: ${ALREADY_PAID}, status: odottaa täydennyserää, tuote: ${AD_HEADLINE}' where key = 'oneOrderOutOfStockSimple' and seller_id is null;

-- oneOrderOutOfStockSimpleWithVat
update message set text = 'Tilausnumero: ${ORDER_ID}, hinta: ${PRODUCT_FINAL_SALES_PRICE_INCL_VAT} (sis. alv ${ALV_PERCENTAGE} %, eli alvin osuus on ${PRODUCT_VAT_AMOUNT}), maksettu: ${ALREADY_PAID}, status: odottaa täydennyserää, tuote: ${AD_HEADLINE}' where key = 'oneOrderOutOfStockSimpleWithVat' and seller_id is null;

-- oneOrderSimple
update message set text = 'Tilausnumero: ${ORDER_ID}, tuote: ${AD_HEADLINE}' where key = 'oneOrderSimple' and seller_id is null;

-- onlyDigitsAllowedInField
update message set text = 'Vaan numeroita sallittu' where key = 'onlyDigitsAllowedInField' and seller_id is null;

-- orderedSuccessfullyFromProviderAfterStockShortage
update message set text = 'Moi,\n\nTuote oli loppunut varastolta (tilaus ${ORDER_ID}) mutta täydennyserä on nyt tullut. Tilauksesi on edennyt.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'orderedSuccessfullyFromProviderAfterStockShortage' and seller_id is null;

-- orderCancelRequestOrderedFromProvider
update message set text = 'Moi,\n\nOlemme vastaanottaneet peruutuspyynnön koskien tilaustanne ${ORDER_ID}.\n\nTäydennyseriä saapuu jatkuvasti ja niitä odotelleet tilaukset käsitellään pikaisesti. Tästä johtuen tilaus on voitu ehtiä paketoida ja antaa postin kuljetettavaksi ennen kuin peruutusvahvistus saavuttaa varaston. Tällöin peruutusta ei luonnollisesti voida enää toimeenpanna. Mikäli saat peruutusvahvistuksen jälkeen viikon sisällä viestin, että tilaus on lähetetty, niin voitte jäädä odottelemaan tilaamanne tuotteen saapumista.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'orderCancelRequestOrderedFromProvider' and seller_id is null;

-- orderCancelRequestPaid
update message set text = 'Moi,\n\nOlemme vastaanottaneet peruutuspyynnön koskien tilaustanne ${ORDER_ID}.\n\nHyvityksen summa: ${REFUND_SUM}\n\nVoit käyttää maksamasi summan toisen tuotteen tai tuotteiden tilaamiseen. Mikäli haluat mieluummin saadan hyvityksen rahana, niin seuraavan linkin kautta pääset syöttämään tilinumeron, johon voimme maksaa hyvityksen.\n\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/refund?confirmationCode=${CONFIRMATION_CODE}&seller=${SIGNATURE_URL_ENC}\n\nHUOM: varmistathan, että tämän lomakkeen täyttäessä käytät vahvistuskoodia ${CONFIRMATION_CODE} eikä viitenumeroa.\n\nNoin viikon sisällä tilauksesta maksamasi rahat palautetaan määrämällesi tilille. Saat erillisen ilmoituksen silloin kun rahat on maksettu.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'orderCancelRequestPaid' and seller_id is null;

-- orderCancelRequestUnpaid
update message set text = 'Moi,\n\nPeruutuspyyntö vastaanotettu (tilaus ${ORDER_ID}).\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'orderCancelRequestUnpaid' and seller_id is null;

-- orderShippedByPostOffice
update message set text = 'Moi,\n\nLähetyksesi jonka tilausnumero on ${ORDER_ID} ${AD_HEADLINE} on nyt lähtenyt kohti Suomea. Ulkomaiden postinseurantapalvelut eivät kuitenkaan tarjoa tietoa Suomen sisällä tapahtuvasta postin lähetyksen etenemisestä. Olemme ilmoittaneet lähetyksesi tiedot www.seuraapostia.fi-palveluun, joka tiedottaa teitä automaattisesti lähetyksen etenemisestä Suomen postiverkossa. Ensimmäisen tiedotteen saat, kun paketti on saapunut Suomeen. Voit tämän jälkeen halutessasi myös itse seurata paketin etenemistä suoraan postin sivuilta: http://www.verkkoposti.com/e3/TrackinternetServlet?lang=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=${TRACKING_CODE}&LOTUS_hae=Hae\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'orderShippedByPostOffice' and seller_id is null;

-- orderShippedByProviderHeaderWithoutTrackingCode
update message set text = 'Seuraava tuote on tänään viety postiin:\n${TRACKING_OBJECT_ORDERS}' where key = 'orderShippedByProviderHeaderWithoutTrackingCode' and seller_id is null;

-- orderShippedByProviderHeaderWithTrackingCode
update message set text = 'Seuraava tuote on tänään viety postiin:\n${TRACKING_OBJECT_ORDERS}\nLähetyksen seurantakoodi on ${TRACKING_CODE}' where key = 'orderShippedByProviderHeaderWithTrackingCode' and seller_id is null;

-- packingProblem
update message set text = 'Moi,\n\nTilauksesi ${ORDER_ID} käsittelyssä on pakattaessa ilmennyt ongelmia. Tiedoitamme asiasta enemmän viikon sisällä.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'packingProblem' and seller_id is null;

-- partlyShippedInfo
update message set text = 'HUOM: nipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen (ne lähetetään erikseen).' where key = 'partlyShippedInfo' and seller_id is null;

-- postCode
update message set text = 'Postinumero' where key = 'postCode' and seller_id is null;

-- productNoLongerForSaleAtProviderOrderedFromProviderAndCanceled
update message set text = 'Moi,\n\nKävipä nolosti. Ehdimme myydä huutoaikana varaston loppuun ko. tuotteen osalta ja tilaamaasi tuotetta ${AD_HEADLINE} tilausnumerolla ${ORDER_ID} ei ole toistaiseksi saatavilla. Tilauksesi on peruutettu.\n\nPahoittelut aiheuttamastamme vaivannäöstäsi. Pyrimme pitämään toiminnan kulut mahdollisimman matalina ja siirtämään aiheutunut hyöty suoraan hintoihin. Asiakkaamme nauttivat poikkeuksellisen edullisista hinnoista, mutta myös satunnaisia tuote-erien loppumisia voi tapahtua.\n\nSaat kahden viikon sisällä lisätietoja hyvityksestä.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'productNoLongerForSaleAtProviderOrderedFromProviderAndCanceled' and seller_id is null;

-- productNoLongerForSaleAtProviderPaidAndCanceled
update message set text = 'Moi,\n\nKävipä nolosti. Ehdimme myydä huutoaikana varaston loppuun ko. tuotteen osalta ja tilaamaasi tuotetta ${AD_HEADLINE} tilausnumerolla ${ORDER_ID} ei ole toistaiseksi saatavilla. Tilauksesi on peruutettu.\n\nPahoittelut aiheuttamastamme vaivannäöstäsi. Pyrimme pitämään toiminnan kulut mahdollisimman matalina ja siirtämään aiheutunut hyöty suoraan hintoihin. Asiakkaamme nauttivat poikkeuksellisen edullisista hinnoista, mutta myös satunnaisia tuote-erien loppumisia voi tapahtua.\n\nHyvityksen summa: ${REFUND_SUM}\n\nVoit käyttää maksamasi summan toisen tuotteen tai tuotteiden tilaamiseen. Mikäli haluat mieluummin saadan hyvityksen rahana, niin seuraavan linkin kautta pääset syöttämään tilinumeron, johon voimme maksaa hyvityksen.\n\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/refund?confirmationCode=${CONFIRMATION_CODE}&seller=${SIGNATURE_URL_ENC}\n\nHUOM: varmistathan, että tämän lomakkeen täyttäessä käytät vahvistuskoodia ${CONFIRMATION_CODE} eikä viitenumeroa.\n\nNoin viikon sisällä tilauksesta maksamasi rahat palautetaan määrämällesi tilille. Saat erillisen ilmoituksen silloin kun rahat on maksettu.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'productNoLongerForSaleAtProviderPaidAndCanceled' and seller_id is null;

-- productNoLongerForSaleAtProviderUnpaid
update message set text = 'Moi,\n\nKävipä nolosti. Ehdimme myydä huutoaikana varaston loppuun ko. tuotteen osalta ja tilaamaasi tuotetta ${AD_HEADLINE} tilausnumerolla ${ORDER_ID} ei ole toistaiseksi saatavilla. Tilaus on peruutettu.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'productNoLongerForSaleAtProviderUnpaid' and seller_id is null;

-- refundToCustomerAccepted
update message set text = 'Moi,\n\nSaatte täyden hyvityksen tilauksesta ${ORDER_ID} (${REFUND_SUM}). Voit käyttää maksamasi summan toisen tuotteen tai tuotteiden tilaamiseen. Mikäli haluat mieluummin saadan hyvityksen rahana, niin seuraavan linkin kautta pääset syöttämään tilinumeron, johon voimme maksaa hyvityksen.\n\n${GOOGLE_ORDER_URL}${CONTEXT_PATH}/refund?seller=${SIGNATURE_URL_ENC}&confirmationCode=${CONFIRMATION_CODE}\n\nVarmennuskoodi hyvitystä varten: ${CONFIRMATION_CODE}\n\nOlette oikeutettu hyvitykseen kahden kuukauden ajan tästä tiedotuksesta.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'refundToCustomerAccepted' and seller_id is null;

-- requiredField
update message set text = 'Pakollinen kenttä' where key = 'requiredField' and seller_id is null;

-- sendToAddress
update message set text = 'Paketti lähetetään osoitteeseenne:\n\n${CUSTOMER_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE}\n${CUSTOMER_CITY}\n\nMikäli haluatte muuttaa osoitetta, johon paketti lähetetään, voitte tehdä sen oheisen linkin kautta: ${GOOGLE_ORDER_URL}${CONTEXT_PATH}/addressChange?seller=${SIGNATURE_URL_ENC}&referenceNumber=${REFERENCE_NUMBER}&firstName=${FIRST_NAME_URL_ENC}&lastName=${LAST_NAME_URL_ENC}&address=${CUSTOMER_ADDRESS_URL_ENC}&postCode=${CUSTOMER_POSTCODE_URL_ENC}&city=${CUSTOMER_CITY_URL_ENC}&emailAddress=${CUSTOMER_EMAIL_URL_ENC}' where key = 'sendToAddress' and seller_id is null;

-- shippedByProviderAddressInfo
update message set text = 'Paketissa on seuraavat vastaanottotiedot:\n${CUSTOMER_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE}\n${CUSTOMER_CITY}' where key = 'shippedByProviderAddressInfo' and seller_id is null;

-- shippedByProviderDelay
update message set text = 'Moi,\n\nTilauksesi ${ORDER_ID} on viety postin toimitettavaksi jo ${DATE}, mutta postin sisäinen käsittely on vielä kesken. Posti kerryttää tilauksia kohdemaahan, jolloin yksittäisissä tapauksissa postin sisäiseen käsittelyyn voi kulua muutamia päiviä tavallista kauemmin.\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'shippedByProviderDelay' and seller_id is null;

-- shippedByProviderGeneralTrackingInfo
update message set text = 'Tiedoitamme sähköpostitse, kun paketti kirjautuu postin seurantajärjestelmään paketin lähtiessä matkaan. Postin käsittely vie yleensä muutaman päivän. Joissakin tilanteissa käsittely vie vähäisistä kohdemaahan lähetettävien kirjeiden määrästä johtuen hieman kauemmin.\n\nHalutessasi voit myös itse seurata lähetyksen etenemistä seuraavassa osoitteessa: ${HONGKONG_OR_CHINA_POST_URL}\nHuomioi kuitenkin, että pakettisi näkyy postin järjestelmässä vasta postin käsittelyjakson jälkeen.' where key = 'shippedByProviderGeneralTrackingInfo' and seller_id is null;

-- spaceMissingFromField
update message set text = 'Välilyönti puuttuu' where key = 'spaceMissingFromField' and seller_id is null;

-- spacesNotAllowedInField
update message set text = 'Välilyönnit ei sallittu' where key = 'spacesNotAllowedInField' and seller_id is null;

-- stockShortageAfterPaid1
update message set text = 'Kävipä nolosti. Varastosaldomme eivät ole olleet ajantasalla tilaamasi tuotteen kohdalla (tilausnumero ${ORDER_ID}). Näin voi käydä esimerkiksi tilanteessa, jossa huutokaupan päättymisestä maksusuorituksen rekisteröitymiseen on kulunut useita päiviä. Tämä voi johtua myös muiden asiakkaiden aiempien tilausten myöhässä saapuneista maksuista, joka on vääristänyt saldotietoja.\n\nTäydennyseriä saapuu jatkuvasti ja niitä odotelleet tilaukset käsitellään pikaisesti. Tiedotamme tilauksen edistymisestä viikon sisällä.\n\nMikäli mielluummin perut tilauksesi, niin se onnistuu seuraavan osoitteen kautta: ${GOOGLE_ORDER_URL}${CONTEXT_PATH}/orderCancelation?seller=${SIGNATURE_URL_ENC}&referenceNumber=${REFERENCE_NUMBER}. Toinen vaihtoehto on jäädä odottamaan täydennystilausta (yleensä tulee viiden päivän sisällä).' where key = 'stockShortageAfterPaid1' and seller_id is null;

-- stockShortageAfterPaid2
update message set text = 'Täydennyserä ei ole tullut (tilausnumero ${ORDER_ID}). Suosittelemme tilauksen peruuttamista osoitteessa ${GOOGLE_ORDER_URL}${CONTEXT_PATH}/orderCancelation?seller=${SIGNATURE_URL_ENC}&referenceNumber=${REFERENCE_NUMBER}. Toinen vaihtoehto on jäädä odottamaan täydennystilausta.\n\nTilauksen peruutuksen jälkeen lähetämme vielä peruutusvahvistuspyynnön oikeuksiesi turvaamiseksi. Peruutusvahvistuksen jälkeen tilaus on peruutettu ja parin viikon sisällä tilauksesta maksamasi rahat palautetaan peruutusvahvistuksen yhteydessä määrämällesi tilille.' where key = 'stockShortageAfterPaid2' and seller_id is null;

-- sumForBundleWithMultipleProducts
update message set text = 'Jo maksettu: ${ALREADY_PAID}\nMaksettava: ${LEFT_TO_PAY}' where key = 'sumForBundleWithMultipleProducts' and seller_id is null;

-- sumForSingleProduct
update message set text = 'Summa: ${TOTAL_PRICE} (sisältää tuotteen hinnan ${FINAL_SALES_PRICE} ja postikulut ${SHIPPING_COST})' where key = 'sumForSingleProduct' and seller_id is null;

-- sumForSingleProductWithVat
update message set text = 'Alvillinen hinta: ${TOTAL_PRICE_INCL_VAT} (sisältää tuotteen hinnan ${PRODUCT_FINAL_SALES_PRICE_INCL_VAT} ja postikulut ${SHIPPING_COST_INCL_VAT}, sis. alv ${ALV_PERCENTAGE} %, eli alvin osuus on ${TOTAL_VAT_AMOUNT})' where key = 'sumForSingleProductWithVat' and seller_id is null;

-- tooLongValueInField
update message set text = 'Arvo liian pitkä' where key = 'tooLongValueInField' and seller_id is null;

-- tooShortValueInField
update message set text = 'Arvo liian lyhyt' where key = 'tooShortValueInField' and seller_id is null;

-- weirdContentsOfField
update message set text = 'Outo arvo' where key = 'weirdContentsOfField' and seller_id is null;

-- yourOrderNumberIs
update message set text = 'Tilausnumeronne on ${ORDER_ID} - ${AD_HEADLINE}' where key = 'yourOrderNumberIs' and seller_id is null;




----------------------------
-- TEXTS FOR TEAM LAUBER: --
----------------------------

-- contextPath
update message set text = '/webstore/googleorder/seller' where key = 'contextPath' and seller_id = 1;


--------------------------
-- TEXTS FOR HK-MYYNTI: --
--------------------------

-- contextPath
update message set text = '/webstore/sellstar/seller' where key = 'contextPath' and seller_id = 2;

-- seller delivery text
update seller set delivery_text = 'Paketti lähetetään kirjattuna kirjeenä eli sisältää seurantakoodin.\n\nTavoitteemme on aina lähettää tuotteet 48:n tunnin sisällä siitä, kun suoritus näkyy pankkitilillämme (pl. viikonloput ja arkipyhät). Lähetämme tuotteen Hong Kong postin kautta lentorahtina. Hong Post postin lupaama toimitusaika pääkaupunkeihin on 4-6 postin työpäivää ja muualle 7-10 postin työpäivää. Toimitusaika riippuu lento- ja maapostin nopeudesta ja on yleisimmin noin kolme viikkoa. Kokonaisuudessaan toimitukseen menee noin neljä viikkoa sisältäen Hong Kong postin sisäisen käsittelyn.\n\nHuomaathan, että ostaessasi meiltä tuotteen sinun maksettavaksesi EI tule tullimaksuja, ALV:a tai muita vastaavia kuluja. Hinta on aina täsmälleen se mitä huudostasi ja toimituskuluista kertyy. Tuote saapuu lähimpään postiin aivan samoin kuin Suomestakin lähetetty kirjattu kirje.\n\nHuuto maksetaan suomalaiselle pankkittilille.\nHUOM: ainoa tuettu maksutapa on tilisiirto (ei esim. postiennakko).\n\nNouto ei luonnollisesti onnistu, koska tuote lähetetään Hong Kongista (Kiinasta).\n\nJos et hyväksy toimitusehtojamme ja ole valmis odottamaan tuotteen toimitusta, niin ethän huuda tätä kohdetta.\n\nPOSTIKULUJEN YHDISTÄMINEN:\nVoit yhdistää postikulut jos ostat useita tuotteita. Ehtoina on:\n1.) Paketit lähetetään samaan osoitteeseen.\n2.) Yhteinen hinta on niin matala, että ALV- ja tullirajat eivät ylity.\n\nHUOM: postikulut yhdistetään automaattisesti. Jos eivät yhdisty, niin tarkista osoitteita joihin mielestäsi niputettavat tuotteet lähetetään ja muuta tarvittaessa osoitetiedot.\n\nHUOM: ei voida enää yhdistää tuotteita nippuun kun nippu on kokonaisuudessaan maksettu.\n\nEli voit kaikessa rauhassa ostaa useita tuotteita ja kun olet valmis, niin voit maksa kaiken pois yhdellä tilisiirrolla. Lisätietoja saat maksuohjeissa.' where id = 2;


--------------------------
-- TEXTS FOR GARDENIA: --
--------------------------

-- contextPath
update message set text = '/hallinta' where key = 'contextPath' and seller_id = 3;

-- bundleInformation
update message set text = 'Postikulujen yhdistäminen:\n--------------------------\nVoit yhdistää postikulut jos ostat useita tuotteita mikäli paketit lähetetään samaan osoitteeseen.\n\nHUOM: postikulut yhdistetään automaattisesti. Jos eivät yhdisty, niin tarkista osoitteita joihin\nmielestäsi niputettavat tuotteet lähetetään ja muuta tarvittaessa osoitetiedot.\n\nHUOM: ei voida enää yhdistää tuotteita nippuun kun nippu on kokonaisuudessaan maksettu.\n\nEli voit kaikessa rauhassa ostaa useita tuotteita ja kun olet valmis, niin voit tilata koontilaskun\nja maksa kaiken pois yhdellä tilisiirrolla.' where key = 'bundleInformation' and seller_id = 3;

-- moneyReceivedForBundleEmail
update message set text = 'Moi,\n\nNippunumerolla ${BUNDLE_ID} tilaamanne tilausten maksu ${TOTAL_PRICE} on nyt kirjautunut tilillemme.\n\nTuotteet lähetetään keskitetyn tilausjärjestelmämme seuraavan lähetyserän mukana eli noin viikon sisällä.\n\nTuotteet lähetetään seuraavin tiedoin:\n\n${CUSTOMER_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE}\n${CUSTOMER_CITY}\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'moneyReceivedForBundleEmail' and seller_id = 3;

-- moneyReceivedEmail
update message set text = 'Moi,\n\nTilausnumerolla ${ORDER_ID} tilaamanne tuotteen ${AD_HEADLINE} maksu ${TOTAL_PRICE} on nyt kirjautunut tilillemme.\n\nTuote lähetetään keskitetyn tilausjärjestelmämme seuraavan lähetyserän mukana eli noin viikon sisällä.\n\nTuote lähetetään seuraavin tiedoin:\n\n${CUSTOMER_NAME}\n${CUSTOMER_ADDRESS}\n${CUSTOMER_POSTCODE}\n${CUSTOMER_CITY}\n\nYstävällisin terveisin,\n${SIGNATURE}' where key = 'moneyReceivedEmail' and seller_id = 3;

-- seller delivery text
update seller set delivery_text = 'Olemme suomalainen puutarhakoneisiin ja -työkaluihin erikoistunut verkkokauppa. Edustamamme Wolf-Garten tunnetaan luotettavuudesta ja laadusta. Kaikki myytävät koneet ja laitteet toimitetaan kotiovellesi ammattimaisin ottein.\n\nHuuto maksetaan tavalliselle pankkittilille.\nHUOM: ainoa tuettu maksutapa on tilisiirto (ei esim. postiennakko).\n\nTuote lähetetään aina postin kautta, eli nouto ei onnistu.\n\nPOSTIKULUJEN YHDISTÄMINEN:\nVoit yhdistää postikulut jos ostat useita tuotteita mikäli paketit lähetetään samaan osoitteeseen.\n\nHUOM: postikulut yhdistetään automaattisesti. Jos eivät yhdisty, niin tarkista osoitteita joihin mielestäsi niputettavat tuotteet lähetetään ja muuta tarvittaessa osoitetiedot.\n\nHUOM: ei voida enää yhdistää tuotteita nippuun kun nippu on kokonaisuudessaan maksettu.\n\nEli voit kaikessa rauhassa ostaa useita tuotteita ja kun olet valmis, niin voit maksa kaiken pois yhdellä tilisiirrolla. Lisätietoja saat maksuohjeissa.' where id = 3;
