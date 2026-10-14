package com.renttool.application.inventory.port.out;

/** Almacén de archivos de imagen. Valida que el contenido sea una imagen admitida. */
public interface ImageStorage {
  /** Guarda la imagen y devuelve la ubicación pública con la que se referenciará. */
  String store(byte[] content);

  void delete(String location);
}
