package taller

/**
 * El punto 5 pide medir aquí, sobre un sistema de al menos un millón de
 * partículas, el tiempo de `pasoSecuencial` y el de `pasoParalelo` con varios
 * umbrales, y reportar la aceleración de cada uno.
 *
 * Lo que hay ahora es el arranque mínimo sobre dos partículas.
 */
object App {

  def main(args: Array[String]): Unit = {
    val sim = new SimulacionParticulas()
    import sim._

    val n = 1000000
    val dt = 0.5
    val azar = new scala.util.Random(42)

    val sistema: Sistema = Array.fill(n)(Particula(
      Vector2(azar.nextDouble() * 100, azar.nextDouble() * 100),
      Vector2(azar.nextDouble()* 2, azar.nextDouble() *2),
      azar.nextDouble()* 5 + 0.1))

    val nucleos = Runtime.getRuntime.availableProcessors
    val umbrales = List(n, 250000, 62500, 15625, 4096, 1024, 256)

    println(s"Núcleos disponibles: $nucleos")
    println(s"Particulas: $n")

    val tSecuencial = medir(5)(pasoSecuencial(sistema, dt))
    println(f"Secuencial: $tSecuencial%.2f ms")
    println()
    println(" Umbral  Paralela(ms)  Aceleración  Aceleración estimada")

    val filas = for (u<-umbrales) yield{
      val tParalelo = medir(5)(pasoParalelo(sistema, dt, u))
      val medida = aceleracion(tSecuencial, tParalelo)
      val estimada = trabajo(n).toDouble / tiempoEstimado(n, nucleos, u)
      f"$u%9d  $tParalelo%12.2f  $medida%11.2f  $estimada%20.2f"
    }
    println(filas.mkString("\n"))

    println()
    println(s"Umbral coveniente: ${umbralConveniente(sistema, dt, umbrales)}")
  }
}
