package com.forensics.platform.ufdr;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.*;

@Component
public class XMLParser {

    public Map<String, Object> parse(MultipartFile file) throws Exception {

        Document document = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file.getInputStream());

        document.getDocumentElement().normalize();

        Map<String, Object> result = new HashMap<>();

        result.put("root", document.getDocumentElement().getNodeName());

        result.put("device", extractDevice(document));

        result.put("contacts", extractContacts(document));

        result.put("messages", extractMessages(document));

        return result;

    }

    private Map<String,Object> extractDevice(Document document){

        Map<String,Object> device = new HashMap<>();

        NodeList list = document.getElementsByTagName("device");

        if(list.getLength()==0)
            return device;

        Element e = (Element) list.item(0);

        device.put("manufacturer", value(e,"manufacturer"));

        device.put("brand", value(e,"brand"));

        device.put("model", value(e,"model"));

        device.put("imei", value(e,"imei"));

        return device;

    }

    private List<Map<String,Object>> extractContacts(Document document){

        List<Map<String,Object>> contacts = new ArrayList<>();

        NodeList list = document.getElementsByTagName("contact");

        for(int i=0;i<list.getLength();i++){

            Element e=(Element)list.item(i);

            Map<String,Object> contact=new HashMap<>();

            contact.put("name",value(e,"name"));

            contact.put("phone",value(e,"phone"));

            contacts.add(contact);

        }

        return contacts;

    }

    private List<Map<String,Object>> extractMessages(Document document){

        List<Map<String,Object>> messages=new ArrayList<>();

        NodeList list=document.getElementsByTagName("message");

        for(int i=0;i<list.getLength();i++){

            Element e=(Element)list.item(i);

            Map<String,Object> sms=new HashMap<>();

            sms.put("from",value(e,"from"));

            sms.put("body",value(e,"body"));

            sms.put("timestamp",value(e,"timestamp"));

            messages.add(sms);

        }

        return messages;

    }

    private String value(Element e,String tag){

        NodeList list=e.getElementsByTagName(tag);

        if(list.getLength()==0)
            return "";

        return list.item(0).getTextContent();

    }

}