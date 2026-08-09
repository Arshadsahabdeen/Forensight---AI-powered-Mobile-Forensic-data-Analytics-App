package com.forensics.platform.ufdr;

import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class LocalMultipartFile implements MultipartFile {

    private final File file;

    public LocalMultipartFile(File file){

        this.file=file;

    }

    @Override
    public String getName(){

        return file.getName();

    }

    @Override
    public String getOriginalFilename(){

        return file.getName();

    }

    @Override
    public String getContentType(){

        return "text/xml";

    }

    @Override
    public boolean isEmpty(){

        return false;

    }

    @Override
    public long getSize(){

        return file.length();

    }

    @Override
    public byte[] getBytes() throws IOException{

        return java.nio.file.Files.readAllBytes(file.toPath());

    }

    @Override
    public InputStream getInputStream() throws IOException{

        return new FileInputStream(file);

    }

    @Override
    public void transferTo(File dest) throws IOException{

        java.nio.file.Files.copy(file.toPath(),dest.toPath());

    }

}