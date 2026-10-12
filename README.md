# Procesamiento secuencial y paralelo de texto

Programa Java para contar palabras, apariciones exactas de una palabra (por defecto `Quijote`) y palabras que contienen una secuencia (por defecto `ción`).

## Requisitos y ejecución

Se requiere JDK 17 o posterior. Desde esta carpeta:

```powershell
javac ProcesadorTexto.java
java ProcesadorTexto DonQuijote.txt Quijote ción 5 100
```

Los argumentos opcionales son: ruta del archivo, palabra exacta, secuencia, número de repeticiones (mínimo 5) y factor de ampliación (por defecto 100). El archivo se carga y el texto ampliado se prepara antes de medir. Cada muestra incluye el procesamiento completo; no incluye lectura ni impresión. El programa muestra promedios y speedup, y comprueba que los conteos de 2 y 4 hilos coincidan con los secuenciales. La ejecución paralela usa `Thread.start()` y `join()` directamente.

