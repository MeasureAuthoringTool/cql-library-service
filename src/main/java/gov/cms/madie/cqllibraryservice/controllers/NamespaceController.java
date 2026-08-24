package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.NamespaceDTO;
import gov.cms.madie.cqllibraryservice.services.NamespaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class NamespaceController {
  private final NamespaceService namespaceService;

  @GetMapping("/namespaces")
  public ResponseEntity<List<NamespaceDTO>> getAllNamespaces() {
    return ResponseEntity.ok(namespaceService.getAllNamespaces());
  }
}
