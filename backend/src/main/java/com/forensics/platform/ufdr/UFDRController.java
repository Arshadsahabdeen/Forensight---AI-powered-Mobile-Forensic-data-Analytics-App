package com.forensics.platform.ufdr;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ufdr")
@RequiredArgsConstructor
public class UFDRController {

    private final UFDRService ufdrService;

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(
            @RequestPart("file") MultipartFile file
    ) throws Exception {

        return ResponseEntity.ok(
                ufdrService.process(file)
        );

    }

}