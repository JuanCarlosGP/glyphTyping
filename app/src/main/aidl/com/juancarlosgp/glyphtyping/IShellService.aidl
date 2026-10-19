package com.juancarlosgp.glyphtyping;

// Servicio que Shizuku ejecuta con permisos de shell. Solo expone UNA orden fija.
interface IShellService {
    void destroy() = 16777114; // código reservado por Shizuku para destruir el servicio
    String enableGlyphDebug() = 1;
}
