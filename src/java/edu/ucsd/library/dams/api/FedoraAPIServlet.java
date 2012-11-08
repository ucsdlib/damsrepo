package edu.ucsd.library.dams.api;

// servlet api
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FedoraAPIServlet extends HttpServlet
{
/*
XML = fedora custom xml
FOX = FOXML
BIN = arbitrary data
TXT = plain text
RDF = RDF/XML

XML GET  /describe                                    static???
XML GET  /objects                                     solr???
XML GET  /objects/[oid]                               objectShow + xsl
FOX GET  /objects/[oid]/export                        objectShow + xsl
FOX GET  /objects/[oid]/objectXML                     objectShow + xsl
XML GET  /objects/[oid]/validate (error message)      ???
XML GET  /objects/[oid]/versions                      objectShow + xsl
XML GET  /objects/[oid]/datastreams                   objectShow + xsl
XML GET  /objects/[oid]/datastreams/[fid]             objectShow + select + xsl
XML GET  /objects/[oid]/datastreams/[fid]/history     objectShow + select + xsl
BIN GET  /objects/[oid]/datastreams/[fid]/content     fileShow
RDF GET  /objects/[oid]/relationships                 ???
XML POST /objects/nextPID                             identifierCreate + xsl
TXT POST /objects/[oid] (pid)                         objectEdit
XML POST /objects/[oid]/datastreams/[fid]             fileUpload
??? POST /objects/[oid]/relationships/new             ???
TXT PUT  /objects/[oid] (updated timestamp)           objectEdit
XML PUT  /objects/[oid]/datastreams/[fid]             fileUpload
TXT DELETE /objects/[oid] (timestamp/array)           objectDelete
TXT DELETE /objects/[oid]/datastreams/[fid] (ts/arr)  fileDelete
??? DELETE /objects/[oid]/relationships               ???
*/
}
