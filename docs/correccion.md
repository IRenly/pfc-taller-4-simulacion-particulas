# Informe de Corrección — Taller 4: Simulación de Partículas en Paralelo

## 1. Fundamentación Teórica y Corrección Formal

Un programa funcional $P_f : A \to B$ es correcto con respecto a una especificación matemática $f : A \to B$ si se cumple:

$$\forall a \in A : P_f(a) = f(a)$$

## Punto 1: Un paso de la simulación

### Especificación

Sea $S = \langle s_0, s_1, \ldots, s_{n-1} \rangle$ un sistema de $n$ partículas, con $s_i = (\vec{r}_i, \vec{v}_i, m_i)$, y sea $dt$ el paso de tiempo. Definimos sobre una partícula:

$$\text{avanza}(s_i, dt) = (\vec{r}_i + \vec{v}_i \cdot dt,\ \vec{v}_i,\ m_i)$$

y, para $0 \leq d \leq h \leq n$, el tramo avanzado entre $d$ (incluido) y $h$ (excluido):

$$\text{tramo}(S, dt, d, h) = \langle \text{avanza}(s_d, dt),\ \ldots,\ \text{avanza}(s_{h-1}, dt) \rangle$$

Se debe cumplir:

- $\text{mover}(p, dt) = \text{avanza}(p, dt)$.
- $\text{pasoSegmento}(S, dt, d, h) = \text{tramo}(S, dt, d, h)$.
- $\text{pasoSecuencial}(S, dt) = \text{tramo}(S, dt, 0, n)$.
- $\text{pasoParalelo}(S, dt, u) = \text{tramo}(S, dt, 0, n)$ para **cualquier** umbral $u$.

### Corrección de `mover`

```scala
def mover(p: Particula, dt: Double): Particula =
  p.copy(pos = p.pos + p.vel * dt)
```

No hay recursión ni iteración, así que basta evaluar. `p.vel * dt` es el vector $(v_x \cdot dt,\ v_y \cdot dt)$ y `p.pos + ...` suma componente a componente, luego la posición nueva es $\vec{r} + \vec{v} \cdot dt$. `copy(pos = ...)` deja `vel` y `masa` con los valores de `p`. Esto es $\text{avanza}(p, dt)$.

### Corrección de `pasoSegmento` y `pasoSecuencial`

```scala
def pasoSegmento(s: Sistema, dt: Double, desde: Int, hasta: Int): Sistema =
  (for (i <- desde until hasta) yield mover(s(i), dt)).toArray
```

La expresión `for ... yield` no tiene bucle ni estado: para cada $i \in [d, h)$ en orden creciente produce $\text{mover}(s_i, dt)$. Por eso el resultado tiene $h - d$ elementos (ninguno si $h \leq d$), y el elemento $j$, con $0 \leq j < h - d$, es $\text{avanza}(s_{d+j}, dt)$. Esto es exactamente $\text{tramo}(S, dt, d, h)$, y `s` solo se lee: el resultado es un arreglo nuevo.

`pasoSecuencial(s, dt)` es `pasoSegmento(s, dt, 0, s.length)`, es decir $\text{tramo}(S, dt, 0, n)$.

**Lema de concatenación.** Sea $\oplus$ la concatenación de arreglos (`++`). Para $d \leq m \leq h$:

$$\text{tramo}(S, dt, d, m) \oplus \text{tramo}(S, dt, m, h) = \text{tramo}(S, dt, d, h)$$

*Demostración.* Los dos lados tienen $(m - d) + (h - m) = h - d$ elementos. Sea $0 \leq j < h - d$.

- Si $j < m - d$, el elemento $j$ del lado izquierdo es el elemento $j$ del primer tramo: $\text{avanza}(s_{d+j}, dt)$.
- Si $j \geq m - d$, es el elemento $j - (m - d)$ del segundo tramo: $\text{avanza}(s_{m + j - (m - d)}, dt) = \text{avanza}(s_{d+j}, dt)$.

En los dos casos coincide con el elemento $j$ de $\text{tramo}(S, dt, d, h)$. $\blacksquare$

### Corrección de `pasoParalelo`

```scala
def pasoParalelo(s: Sistema, dt: Double, umbral: Int): Sistema = {
  val piso = Math.max(umbral, 1)

  def paso(desde: Int, hasta: Int): Sistema =
    if (hasta - desde <= piso) pasoSegmento(s, dt, desde, hasta)
    else {
      val mitad = desde + (hasta - desde) / 2
      val (izquierda, derecha) = parallel(paso(desde, mitad), paso(mitad, hasta))
      izquierda ++ derecha
    }

  paso(0, s.length)
}
```

**Sobre `parallel`.** `parallel(a, b)` devuelve la pareja de los valores de `a` y de `b`, en ese orden. Las dos expresiones solo leen `s` y construyen arreglos nuevos; ninguna escribe en algo que la otra lea. Por eso el resultado no depende de cuál termine primero, y se comporta igual que evaluar `a` y luego `b`.

**Teorema.** Para todo $0 \leq d \leq h \leq n$: $\text{paso}(d, h) = \text{tramo}(S, dt, d, h)$.

*Demostración* por inducción fuerte sobre $k = h - d$, con $\text{piso} \geq 1$.

- **Caso base, $k \leq \text{piso}$.** El condicional toma la primera rama y `paso(d, h)` es `pasoSegmento(s, dt, d, h)`, que por lo anterior es $\text{tramo}(S, dt, d, h)$.
- **Caso inductivo, $k > \text{piso}$.** Como $\text{piso} \geq 1$, se tiene $k \geq 2$. Sea $m = d + \lfloor k/2 \rfloor$. Entonces $1 \leq \lfloor k/2 \rfloor \leq k - 1$, luego $d < m < h$. Los dos tramos miden $\lfloor k/2 \rfloor < k$ y $k - \lfloor k/2 \rfloor < k$, así que por hipótesis de inducción $\text{paso}(d, m) = \text{tramo}(S, dt, d, m)$ y $\text{paso}(m, h) = \text{tramo}(S, dt, m, h)$. Entonces `paso(d, h)` devuelve $\text{tramo}(S, dt, d, m) \oplus \text{tramo}(S, dt, m, h)$, que por el lema de concatenación es $\text{tramo}(S, dt, d, h)$. $\blacksquare$

**Conclusión.** `pasoParalelo(s, dt, u)` es `paso(0, s.length)`, es decir $\text{tramo}(S, dt, 0, n) = \text{pasoSecuencial}(S, dt)$, **sea cual sea el umbral** $u$.

### Partir el sistema no pierde ni repite partículas

Los dos tramos de cada partición son $[d, m)$ y $[m, h)$ con $d < m < h$.

- **No se repite ninguna:** un índice $i$ está en el primero si $d \leq i < m$ y en el segundo si $m \leq i < h$. Las dos condiciones no pueden cumplirse a la vez.
- **No se pierde ninguna:** todo $i$ con $d \leq i < h$ cumple $i < m$ o $i \geq m$, así que está en uno de los dos.

Aplicado en cada nivel, los tramos de las hojas son disjuntos y su unión es $[0, n)$. Por ejemplo, con $n = 7$ y umbral 2 las hojas son $[0,1)$, $[1,3)$, $[3,5)$ y $[5,7)$, con $1 + 2 + 2 + 2 = 7$ partículas. Esto vale también cuando $k$ es impar, porque la mitad derecha absorbe la partícula que sobra.

### La operación que combina es asociativa

Los resultados parciales se combinan con $\oplus$, y es asociativa: para tres arreglos $A$, $B$, $C$,

$$(A \oplus B) \oplus C = A \oplus (B \oplus C)$$

*Demostración.* Los dos lados tienen $|A| + |B| + |C|$ elementos. El elemento $j$ de cualquiera de los dos es el elemento $j$ de $A$ si $j < |A|$; el elemento $j - |A|$ de $B$ si $|A| \leq j < |A| + |B|$; y el elemento $j - |A| - |B|$ de $C$ en el resto. $\blacksquare$

El arreglo vacío es el neutro, y por eso los tramos vacíos no alteran el resultado. La concatenación no es conmutativa, y no tiene por qué serlo: `paso` pone siempre `izquierda` antes que `derecha`, sin importar cuál mitad termine primero.

### Terminación

En cada llamada recursiva el tamaño baja estrictamente: de $k$ a $\lfloor k/2 \rfloor$ y a $k - \lfloor k/2 \rfloor$, ambos menores que $k$ cuando $k \geq 2$. Como solo se parte con $k > \text{piso} \geq 1$, siempre se cumple $k \geq 2$ y los tamaños llegan a un valor menor o igual que el piso en un número finito de pasos.

Sin el piso el argumento falla: con umbral 0 y un tramo de tamaño 1 se cumpliría $k > 0$, la mitad sería $\lfloor 1/2 \rfloor = 0$ y el tramo derecho volvería a ser $[d, h)$, con el mismo tamaño, sin terminar nunca. Por eso `piso = Math.max(umbral, 1)`.

---

## 2. Punto 2: Magnitudes Globales (Secuencial)

### 2.1. Especificación Matemática

Sea un sistema de partículas $S = \langle p_0, p_1, \ldots, p_{n-1} \rangle$, donde cada partícula $p_i$ posee:
* Posición: $\vec{r}_i = (x_i, y_i) \in \mathbb{R}^2$.
* Velocidad: $\vec{v}_i = (v_{x,i}, v_{y,i}) \in \mathbb{R}^2$ con norma $\|\vec{v}_i\| = \sqrt{v_{x,i}^2 + v_{y,i}^2}$.
* Masa: $m_i \in \mathbb{R}_{\geq 0}$.

Se definen formalmente las siguientes funciones:

1. **Energía Cinética Total ($E$)**:
   $$E(S) = \sum_{i=0}^{n-1} \frac{1}{2} m_i \|\vec{v}_i\|^2$$
   Con $E(\emptyset) = 0$.

2. **Masa Total ($M$)**:
   $$M(S) = \sum_{i=0}^{n-1} m_i$$
   Con $M(\emptyset) = 0$.

3. **Centro de Masa ($\vec{R}_{cm}$)**:
   $$\vec{R}_{cm}(S) = \begin{cases} (0, 0) & \text{si } M(S) = 0 \\ \frac{1}{M(S)} \sum_{i=0}^{n-1} m_i \vec{r}_i & \text{si } M(S) > 0 \end{cases}$$

---

### 2.2. Demostración de Corrección

#### A. Corrección de `energiaSecuencial`
La implementación en Scala es:
```scala
def energiaSecuencial(s: Sistema): Double =
  s.map(p => 0.5 * p.masa * (p.vel.norma * p.vel.norma)).sum
```

* **Lema 1 (Evaluación escalar por partícula)**:
  Para cada partícula $p_i$, `p.vel.norma * p.vel.norma` evalúa $(\sqrt{v_{x,i}^2 + v_{y,i}^2})^2 = v_{x,i}^2 + v_{y,i}^2 = \|\vec{v}_i\|^2$.
  Al multiplicar por `0.5 * p.masa`, se obtiene exactamente $\frac{1}{2} m_i \|\vec{v}_i\|^2$.
* **Lema 2 (Preservación del homomorfismo aditivo)**:
  La operación `.sum` sobre el vector resultante efectúa $\sum_{i=0}^{n-1} e_i$. Para el caso base $n = 0$ (sistema vacío), la suma sobre una secuencia vacía retorna neutro aditivo $0.0$, coincidiendo con la especificación $E(\emptyset) = 0$.
  Por lo tanto, `energiaSecuencial(s)` computa exactamente $E(S)$.

#### B. Corrección de `masaTotal`
La implementación es:
```scala
def masaTotal(s: Sistema): Double =
  s.map(_.masa).sum
```
* Para cualquier sistema $S$, la proyección `s.map(_.masa)` genera la secuencia $\langle m_0, m_1, \ldots, m_{n-1} \rangle$.
* La invocación de `.sum` calcula la sumatoria $\sum_{i=0}^{n-1} m_i$. Para $S = \emptyset$, `.sum` retorna $0.0 = M(\emptyset)$.

#### C. Corrección de `centroDeMasaSecuencial`
La implementación es:
```scala
def centroDeMasaSecuencial(s: Sistema): Vector2 = {
  val mTotal = masaTotal(s)
  if (mTotal == 0.0) Vector2(0.0, 0.0)
  else {
    val sumaPonderada = s.foldLeft(Vector2(0.0, 0.0)) { (acc, p) =>
      acc + (p.pos * p.masa)
    }
    Vector2(sumaPonderada.x / mTotal, sumaPonderada.y / mTotal)
  }
}
```

* **Caso 1: $M(S) = 0$ (sin masa o sistema vacío)**:
  La condición `if (mTotal == 0.0)` se evalúa como verdadera y retorna `Vector2(0.0, 0.0)`. Esto satisface directamente la cláusula de frontera de la definición matemática y previene divisiones por cero.
* **Caso 2: $M(S) > 0$**:
  Demostramos por inducción que tras procesar el prefijo $S_k = \langle p_0, \ldots, p_{k-1} \rangle$, el acumulador de `foldLeft` equivale a $\vec{P}_k = \sum_{i=0}^{k-1} m_i \vec{r}_i$:
  - **Base ($k = 0$)**: El acumulador inicial es `Vector2(0.0, 0.0)` $= \sum_{i=0}^{-1} m_i \vec{r}_i = \vec{0}$.
  - **Paso inductivo ($k \to k+1$)**: Si el acumulador contiene $\vec{P}_k$, al procesar $p_k$ se suma $\vec{P}_k + (p_k.\text{pos} \cdot p_k.\text{masa}) = \sum_{i=0}^{k-1} m_i \vec{r}_i + m_k \vec{r}_k = \sum_{i=0}^{k} m_i \vec{r}_i = \vec{P}_{k+1}$.
  - Al completar los $n$ elementos, `sumaPonderada` contiene $\sum_{i=0}^{n-1} m_i \vec{r}_i$.
  - Finalmente, al dividir las componentes $x$ e $y$ entre $mTotal$, se obtiene:
    $$\left( \frac{\sum m_i x_i}{M}, \frac{\sum m_i y_i}{M} \right) = \frac{1}{M} \sum_{i=0}^{n-1} m_i \vec{r}_i = \vec{R}_{cm}(S)$$

---

### 2.3. Propiedad de Asociatividad y Reparto
Una propiedad esencial exigida en el taller es que la suma vectorial y escalar es **asociativa y conmutativa**:

$$\sum_{p \in A \cup B} m_p \vec{r}_p = \left( \sum_{p \in A} m_p \vec{r}_p \right) + \left( \sum_{p \in B} m_p \vec{r}_p \right)$$
$$M(A \cup B) = M(A) + M(B)$$

Por esta razón, la suma ponderada y la masa se pueden repartir en mitades arbitrarias sin alterar el resultado. En contraste, el promedio directo de centros de masa parciales no es asociativo cuando las masas de los segmentos difieren:

$$\frac{\vec{R}_{cm}(A) + \vec{R}_{cm}(B)}{2} \neq \frac{M_A \vec{R}_{cm}(A) + M_B \vec{R}_{cm}(B)}{M_A + M_B} = \vec{R}_{cm}(A \cup B)$$

Nuestra implementación acumula explícitamente la suma ponderada y la masa total, garantizando la consistencia tanto en la versión secuencial como en su posterior extensión concurrente.
# Punto 3: informe de corrección

## 1. Corrección de `energiaParalela`

Sea un sistema de partículas `s` de tamaño \(n\). La energía cinética total está definida por:

$$
E(s)=\sum_{i=0}^{n-1}\frac{1}{2}m_i\|\vec v_i\|^2
$$

La función divide el intervalo inicial \([0,n)\) en dos segmentos cuando su tamaño supera el umbral. Si el punto medio es \(k\), los segmentos son:

$$
[0,k) \qquad [k,n)
$$

Estos segmentos son disjuntos y su unión es el intervalo original:

$$
[0,k)\cap[k,n)=\varnothing
$$

$$
[0,k)\cup[k,n)=[0,n)
$$

Por lo tanto, ninguna partícula se pierde ni se procesa dos veces.

Cada llamada recursiva calcula la suma de energía de su segmento. Los resultados se combinan mediante la suma:

$$
E(s)=E(s_{izq})+E(s_{der})
$$

La suma es asociativa:

$$
(a+b)+c=a+(b+c)
$$

Por esta propiedad, los resultados parciales pueden combinarse independientemente del orden en que terminen las tareas paralelas. En consecuencia, la función devuelve la misma suma que la versión secuencial, salvo las pequeñas diferencias de redondeo propias de la aritmética de punto flotante.

## 2. Corrección de `centroDeMasaParalelo`

El centro de masa de un sistema se define como:

$$
CM(s)=
\frac{\sum_{i=0}^{n-1}m_i\vec p_i}
{\sum_{i=0}^{n-1}m_i}
$$

si la masa total es distinta de cero. Si la masa total es cero, el resultado es el vector origen.

Para repartir el cálculo, cada segmento no devuelve directamente su centro de masa. Devuelve el par:

$$
R(s)=(W(s),M(s))
$$

donde:

$$
W(s)=\sum_{i\in s}m_i\vec p_i
$$

$$
M(s)=\sum_{i\in s}m_i
$$

Para dos segmentos disjuntos \(A\) y \(B\), la operación de combinación es:

$$
(W_A,M_A)\oplus(W_B,M_B)
=(W_A+W_B,M_A+M_B)
$$

Esta operación es asociativa porque la suma de vectores y la suma de números reales son asociativas:

$$
((W_A,M_A)\oplus(W_B,M_B))\oplus(W_C,M_C)
$$

$$
=(W_A+W_B+W_C,\ M_A+M_B+M_C)
$$

$$
=(W_A,M_A)\oplus((W_B,M_B)\oplus(W_C,M_C))
$$

La división se realiza una sola vez, después de combinar todos los segmentos:

$$
CM(s)=
\begin{cases}
(0,0), & M(s)=0\\
W(s)\cdot\frac{1}{M(s)}, & M(s)\ne 0
\end{cases}
$$

Así se obtiene el mismo centro de masa que en la definición secuencial.

## 3. Por qué no se pueden promediar los centros parciales

Los centros parciales no se pueden promediar directamente porque cada segmento puede tener una masa diferente. Un promedio simple asignaría el mismo peso a ambos segmentos, aunque uno contenga más masa que el otro.

Por ejemplo, si un segmento tiene masa \(3\) y suma ponderada \(W_A\), y el otro tiene masa \(5\) y suma ponderada \(W_B\), el centro correcto es:

$$
CM=\frac{W_A+W_B}{3+5}
$$

No es, en general:

$$
\frac{1}{2}\left(\frac{W_A}{3}+\frac{W_B}{5}\right)
$$

Por eso se combinan primero las sumas ponderadas y las masas, y se divide al final.

## Punto 4: Trabajo y profundidad

### `trabajo( )`

La función devuelve el mismo número de operaciones que recibe.

#### Especificación:

Para cualquier entero n:

$$
trabajo(n) = n
$$

#### Demostración.

La función no realiza transformaciones sobre el valor recibido. Simplemente devuelve n.

Por lo tanto, el resultado coincide con el número total de operaciones definido en la especificación.

### `profundidad( )`

La función calcula la profundidad de las divisiones, sumando uno por cada nivel de división y considerando el tamaño del tramo cuando se alcanza el umbral.

#### Especificación.

Sea $D(n,u)$ la profundidad calculada para n operaciones y un umbral u:

$$
D(n,u) =
\begin{cases}
n & \text{si } n \leq u \
1 + \max(D(\lfloor n/2 \rfloor,u),\
\qquad D(\lceil n/2 \rceil,u))
& \text{si } n > u
\end{cases}
$$

Esta definición corresponde a la división entera realizada por n / 2 y a la segunda parte n - mitad.

#### Demostración por inducción sobre n.

**Caso base**: $n \leq u$.

La función ejecuta if (n <= piso) y devuelve n.

Como umbral es positivo, piso es igual a umbral. Por lo tanto, el resultado coincide con la especificación:

$$
D(n,u) = n
$$

#### Hipótesis de inducción (HI):

Suponemos que aux calcula correctamente la profundidad para cualquier valor menor que n.

**Paso inductivo**: $n > u$.

**La función calcula:** `val mitad = n / 2`

**Después realiza las llamadas:**

`aux(mitad)`
`aux(n - mitad)`

Como $n > u \geq 1$, ambas partes son menores que n. Por la hipótesis de inducción, las dos llamadas calculan correctamente sus respectivas profundidades.

**Finalmente, se ejecuta:**

`math.max(aux(mitad), aux(n - mitad)) + 1`

Se toma la mayor profundidad de las dos partes y se suma uno para contar la división actual.

Por lo tanto, el resultado coincide con la especificación.

**Terminación:**

En cada llamada recursiva, n se divide en dos partes menores que el valor original. Como ambas partes son enteros no negativos y menores que n, eventualmente llegan a ser menores o iguales al umbral.

En ese momento se alcanza el caso base y la recursión termina.

Por inducción, profundidad calcula correctamente el resultado para todo n no negativo y umbral positivo.

### `tiempoEstimado( )`

La función suma la profundidad del proceso y el trabajo distribuido entre los procesadores disponibles.

#### Especificación:

Para $n \geq 0$, $p > 0$ y $u > 0$:

$$
tiempoEstimado(n,p,u) =
D(n,u) + \left\lfloor \frac{n}{p} \right\rfloor
$$

Se utiliza la división entera porque trabajo(n) y p son valores de tipo Int.

#### Demostración.

La función realiza las siguientes operaciones:

- Comprueba que p > 0. Si no se cumple, lanza una excepción mediante require.

- Llama a profundidad(n, umbral), que por la demostración anterior devuelve correctamente $D(n,u)$.

- Llama a trabajo(n), que devuelve n.

- Divide el resultado entre p. Como ambos valores son enteros, obtiene $\lfloor n/p \rfloor$.

- Suma ambos resultados y devuelve el valor como Double.

Por lo tanto:

$$
\begin{aligned}
tiempoEstimado(n,p,u)
&= profundidad(n,u) + trabajo(n)/p \
&= D(n,u) + \left\lfloor \frac{n}{p} \right\rfloor
\end{aligned}
$$

El resultado coincide con la especificación.

#### Terminación.

tiempoEstimado realiza una cantidad finita de operaciones y llama a profundidad, cuya terminación ya fue demostrada. La función trabajo devuelve directamente su argumento, por lo que también termina.

En consecuencia, para entradas que cumplen las condiciones de los require, tiempoEstimado termina y devuelve el resultado especificado.


# Punto 5: medir la aceleración

## 1. Corrección de `aceleracion`

La aceleración se define como la relación entre el tiempo de ejecución secuencial y el tiempo de ejecución paralelo.

Su especificación matemática es:

$$
A = \frac{T_s}{T_p}
$$

Donde:

* $T_s$ representa el tiempo secuencial.
* $T_p$ representa el tiempo paralelo.

La función realiza directamente la división entre los dos tiempos recibidos.

Si ambos tiempos son positivos y el tiempo paralelo es menor que el secuencial, el resultado será mayor que uno, indicando una mejora en la ejecución.

Por lo tanto, la función calcula correctamente la aceleración definida en la especificación.

## 2. Corrección de `umbralConveniente`

La función busca el umbral que produce el menor tiempo de ejecución entre una lista de candidatos.

Su comportamiento se puede expresar como:

$$
U = \min_{u \in L} T(u)
$$

Donde:

* $L$ es la lista de umbrales candidatos.
* $T(u)$ es el tiempo medido utilizando el umbral $u$.
* $U$ es el umbral seleccionado.

La implementación realiza dos casos:

### Caso 1: lista vacía

Si la lista de umbrales está vacía, devuelve `s.length`.

Esto equivale a utilizar un umbral igual al tamaño del sistema, por lo que no es necesario dividir el trabajo.

### Caso 2: lista con candidatos

Para cada umbral se ejecuta `pasoParalelo` y se mide su tiempo utilizando tres repeticiones.

Después se utiliza `minBy(_._2)` para seleccionar el elemento con el menor tiempo registrado.

Finalmente, se devuelve el umbral correspondiente a ese resultado.

Por lo tanto, la función selecciona correctamente el umbral con menor tiempo medido dentro de la lista proporcionada.

## 3. Corrección de `medir`

La función realiza varias mediciones del mismo proceso utilizando ScalaMeter.

Primero se ejecuta el proceso la cantidad de veces indicada por `repeticiones`.

Después se ordenan las mediciones de menor a mayor mediante `sorted`.

Finalmente, se selecciona el elemento central del vector.

Para cinco mediciones, el resultado corresponde a la tercera medición una vez ordenadas.

De esta manera, se utiliza la mediana para reducir la influencia de valores extremos y obtener una medida más representativa del tiempo de ejecución.

## 4. Corrección del proceso de medición

En `App.scala` se mide primero el tiempo secuencial y después el tiempo paralelo para cada umbral.

La aceleración se calcula mediante:

$$
A(u) = \frac{T_{\text{secuencial}}}{T_{\text{paralelo}}(u)}
$$

También se calcula la aceleración estimada utilizando las funciones del punto 4.

Finalmente, se muestran los resultados y se busca el umbral conveniente.

La comparación permite observar si la aceleración real se aproxima a la estimada.

Es importante tener en cuenta que los tiempos dependen de los núcleos disponibles, de la máquina y de otros procesos que se estén ejecutando.

Por esta razón, las mediciones permiten comparar el comportamiento de los diferentes umbrales, pero no garantizan los mismos tiempos en todos los equipos.
