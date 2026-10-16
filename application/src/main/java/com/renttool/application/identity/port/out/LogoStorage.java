package com.renttool.application.identity.port.out;

/** Almacén de logos de proveedor. Valida que el contenido sea una imagen admitida. */
public interface LogoStorage {
  /** Guarda el logo y devuelve la ubicación pública con la que se referenciará. */
  String store(byte[] content);

  void delete(String location);
}
