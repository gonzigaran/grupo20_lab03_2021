# Informe Lab3: Programación Concurrente Usando Actores en Scala

### Gonzalo Zigarán
> Paradigmas de la Programación 2021

## Arquitectura del Sistema

El diseño del sistema está inspirado en el diseño de la aplicación de la guía de iniciación de Akka, realizando un paralelismo entre los `Device` y los `Feed`, en el caso de la guía los `Devices` están agrupados en `DeviceGroup`; en este caso, vamos a agrupar a los `Feeds` por su url, en `UrlManager`. Todo supervisado por un `Supervisor`, en correspondencia con el `DeviceManager` de la guía. 

![Actores del Sistema](actores.png)

- **Supervisor**: este actor es el que gobierna el sistema, se encarga de la comunicación con lo externo y crea los `UrlManager` para cada url que lee del archivo de suscripciones. Este actor recibe una lista de subscripciones y para cada elemento de la lista, crea un actor `UrlManager`, enviándole la lista de feeds.

- **UrlManager**: este actor administra los feeds de un sitio, encargandose de crear los actores `Feed` que corresponde a cada feed. Este actor, recibe de `Supervisor` el url, y la lista de feeds, y con esa información crea un actor para cada feed.

- **Feed**: este actor está asociado a un único feed, y sus tareas son obtener los feeds para las especificaciones del actor e imprimirlos (más adelante, deberá devolverlo al `UrlManager`). Este actor recibe la url ya formada para su feed, y realiza la consulta, luego imprime en pantalla el resultado (más adelante debera devolverlo a `UrlManager`)

Los mensajes que se transmiten entre los actores planteados se puede ver reflejado en el siguiente gráfico, donde además se puede observar como se relaciona con el mundo exterior al sistema:

![Mensajes entre los Actores](mensajes.png)

## Actores para subscribirse

Para esta sección, reutilizamos la función `readSubscriptions` del laboratorio anterior, que se encarga de leer las subscripciones de un `json` y convertirlo en un `List[Suscriptcion]`. Luego implementamos los actores mencionados en la parte anterior, en donde los actores `Feed` reutilizan los `Parser` creados en el lab anterior e imprimen por consola lo que obtienen. 

## Request-Response

Para esta parte del laboratorio, se va a modificar el intercambio de mensajes entre los actores. Ya que al trabajar con solicitudes y respuestas, se puede lograr un implementación más acabada de lo que se busca en este proyecto. 

Por un lado se va a separar la parte de realizar las subscripciones, y la parte de obtener los datos. Cuando el sistema comience, se va a enviar al `Supervisor` las subscripciones del archivo de entrada, y se crearán todos los actores necesarios, manteniendo un registro de quienes son los *hijos* de cada actor a partir de la respuesta.

Luego, la aplicación le solicitará al `Supervisor` que obtenga los mensajes para cada feed; este se lo solicitará a los `UrlManager` y estos a cada `Feed` particular. La información se va respondiendo hacia arriba, el `Supervisor` recolecta todo y lo imprime por pantalla. 

Para la implementación de este modelo, se utilizó el patrón de interacción propuesto en las consignas del laboratorio, `ask-pattern`, ya que tiene algunas ventajas por sobre los otros. El actor que envía el mensaje puede saber si el actor destinatario recibió y procesó el mensaje. También con este método no se sobrecarga de mensjes para asegurar que se recibió y además de contar con un timer en el caso de que algún actor se demore mucho en responder. 

Para esta implementación, se utilizó el siguiente protocolo:

- El `Supervisor` pregunta a cada `UrlManager` por la información
- Luego, cada `UrlManager` le solicita a cada `Feed` que busque la información en un url asignada, y se la devuelva. Una vez que recorre todos los `Feeds` le comunica al `Supervisor` que ya terminó.
- Cada respuesta de los `Feeds` lo enviará su `UrlManager` correspondiente al `Supervisor` y este lo imprimirá por consola

## Integración

En esta parte se realiza la integración de todo lo propuesto, utilizando el parser desarrollado en el laboratorio 2 para obtener los datos de cada pedido HTTP.

## Investigación

### Si quisieran extender el sistema para soportar el conteo de entidades nombradas del laboratorio 2, ¿qué parte de la arquitectura deberían modificar? Justificar.

Una opción de implementar esto, sería agregar un actor más que se encargue de procesar el conteo de entidades, y luego, cada vez que el `Supervisor` reciba el texto de algún `Feed` para imprimir, que en vez de eso, lo envíe a este nuevo actor. Una vez que el nuevo actor recolecte todos los textos, puede realizar el conteo de palabras y pasarselo al `Supervisor` para que lo imprima en pantalla.

### Si quisieran exportar los datos (ítems) de las suscripciones a archivos de texto (en lugar de imprimirlas por pantalla):

#### ¿Qué tipo de patrón de interacción creen que les serviría y por qué? (hint: es mejor acumular todo los items antes de guardar nada).

#### ¿Dónde deberían utilizar dicho patrón si quisieran acumular todos los datos totales? ¿Y si lo quisieran hacer por sitio?

Para este caso, en el que es mejor reunir toda la información antes de almacenarla en un archivo de texto, tal vez conviene utilizar el patrón de interacción **Per session child Actor**, porque es una buena opción para cuando un actor requiere la respuesta de varios actores antes de continuar.

En este caso, el `Supervisor` solicita la información a todos los `UrlManagers` y luego estos le solicitan a sus `Feeds`. Cada `UrlManager` recolecta toda la información de cada `Feed` y recién al finalizar envía al `Supervisor`. En caso de querer ir imprimiendo por sitio en diferentes archivos, este protocolo también facilita esa operación. 

### ¿Qué problema trae implementar este sistema de manera síncrona?

Claramente esta arquitectura realiza la tarea en mucho menos tiempo, al aprovechar los recursos para realizar las tareas en simultaneo. Pero más allá de optimizar los recursos, realizar esta implementación de manera sincónica podría traer varios problemas porque se realizan muchas consultas HTTP a sitios externos al sistema, lo que podría ocacionar varios problemas, y en cualquier caso, si una consulta tiene problemas, retrasa todo el sistema. Un conflicto en una consulta podría interrumpir todo el proceso, en cambio con esta arquitectura, se cae solamente el actor correspondiente y el resto del sistema sigue procesando la información.


### ¿Qué les asegura el sistema de pasaje de mensajes y cómo se diferencia con un semáforo/mutex?

El sistema de pasaje de mensajes nos asegura que no se puede acceder al estado interno de cada mensaje, por lo que los estados internos no son considerados regiones críticas. Asumimos que los mensajes no van a llegar en el mismo orden siempre a los actores, por lo que no tenemos que preocuparnos por esa sincronización con semáforos que vayan pausando la ejecución.

## **Punto estrella:** Subscripción a _Reddit/Json_

La implementación de este punto estrella se encuentra en la rama `json`. Para realizar esto, se cambio el protocolo del tipo de dato `Subscription`, agregandole el atributo `urlType`. 
Luego, este atributo es pasado por `UrlManager` a cada `Feed` y este lo almacena como un atributo interno del actor. 

Luego, cuando el feed tenga que obtener la información mediante una consulta Http, crea un Parser de acuerdo al tipo de url, puede ser un `RSSParser` o un `RedditParser`, utilizando lo ya realizado en el laboratorio 2. Luego el funcionamiento es igual. 