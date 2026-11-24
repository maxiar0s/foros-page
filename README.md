# Foro: servicios de usuarios y foro

Arranca Oracle Database Free, los dos servicios REST y la aplicación Angular con Docker Compose. El montaje es una integración local para demostración: las credenciales de `.env.example` y cuentas seed son públicas de ejemplo, no secretos de producción.

## Inicio rápido

Requisitos: Docker Engine/Desktop con Compose v2. Clona/descarga el repositorio y, desde su raíz:

```sh
cp .env.example .env
# Edita .env para configurar contraseñas locales y un JWT_SECRET base64 propio.
docker compose config -q
docker compose up --build -d
docker compose ps
```

Espera a que `oracle`, `users-service`, `forum-service` y `moderation-service` indiquen `healthy`; el frontend se inicia cuando los tres servicios API están listos. La primera inicialización de Oracle puede tardar varios minutos. URLs: aplicación web `http://localhost:4200`, users `http://localhost:8081`, forum `http://localhost:8082`, Oracle `localhost:1521/FREEPDB1`. Moderation se consume desde el mismo origen del frontend mediante `/moderation-api/`; no publica puerto de host. El frontend enruta `/users-api/` a users, `/forum-api/` a forum y `/moderation-api/` a moderation en el mismo origen; utiliza los puertos internos y no requiere configurar CORS en el navegador. Puedes cambiar el puerto público del frontend con `FRONTEND_PORT` en `.env`. El volumen nombrado `foro-oracle-data` conserva los datos; los scripts de `database/init` solo se ejecutan al inicializar por primera vez un volumen vacío. No elimines volúmenes para reiniciar servicios; `docker compose down` detiene y retira contenedores, pero conserva el volumen. Para borrar datos se requiere una acción deliberada fuera del procedimiento normal.

Después de actualizar/reconstruir los servicios, los tokens de usuario existentes quedan invalidados; inicia sesión nuevamente en la aplicación. La recuperación de contraseña depende de SMTP configurado y de que el esquema incluya la tabla de recuperación descrita en la sección de migración; los valores SMTP predeterminados son solo de desarrollo y no entregan correo.

El contenedor crea un usuario de aplicación de Oracle a partir de `ORACLE_APP_USER` y `ORACLE_APP_PASSWORD`; ambos servicios usan esa cuenta y su propio conjunto de tablas. En una inicialización nueva, el hook conecta explícitamente como ese usuario a `FREEPDB1` y ejecuta `database/init/01-forum-schema-and-seed.sql`; no se conecta como `SYS` ni usa el contenedor raíz. Los fallos SQL o de ejecución terminan la inicialización con error. El script no imprime credenciales. `ORACLE_SYSTEM_PASSWORD`, credenciales de la aplicación y `JWT_SECRET` son editables localmente en `.env`. No subir `.env`, ni usar valores de ejemplo en producción. Usa valores aleatorios robustos, gestión de secretos y TLS en entornos no locales.

### SMTP y recuperación de contraseña

Compose pasa al `users-service` estas variables, sin valores secretos predeterminados: `MAIL_HOST` (`localhost`), `MAIL_PORT` (`25`), `MAIL_USERNAME` y `MAIL_PASSWORD` (vacíos), `MAIL_SMTP_AUTH` (`false`), `MAIL_SMTP_STARTTLS` (`false`), `MAIL_FROM` (`noreply@example.test`) y `RECOVERY_FRONTEND_URL` (`http://localhost:4200`). Son valores de demostración; localhost dentro del contenedor no configura un relay SMTP. Para habilitar envío real, configura en el entorno local las variables del proveedor SMTP, habilita autenticación/STARTTLS según sus requisitos y proporciona el URL de recuperación del frontend. No pongas contraseñas reales en archivos versionados; para producción usa gestión de secretos y TLS.

### Migración manual del esquema de recuperación

La migración `database/migrations/001-password-recovery.sql` es manual y aditiva: crea `PASSWORD_RESET_TOKENS`, su índice y una FK a `APP_USERS`. **No se ejecuta automáticamente**, ni para bases existentes ni al crear un volumen nuevo. Compose configura `DDL_AUTO=validate`, por lo que la tabla debe estar presente antes de desplegar/arrancar `users-service` con la recuperación.

Antes de aplicarla, el operador debe obtener y verificar una copia de seguridad, confirmar que está conectado al PDB `FREEPDB1` y al esquema dueño correcto, revisar el SQL y comprobar el estado actual de los objetos. Aplicar el archivo una sola vez mediante una herramienta SQL aprobada y con autorización; luego verificar la tabla, el índice y la FK antes de iniciar el servicio. No ejecutar a ciegas sobre objetos existentes ni modificar datos de usuarios. En una base existente, la aplicación de esta migración requiere una acción manual deliberada; el arranque de Compose nunca debe aplicarla automáticamente.

## Datos de demostración

En un volumen recién creado, el SQL crea `APP_USERS`, `FORUM_CATEGORY`, `FORUM_TOPIC` y `FORUM_COMMENT`, con tres usuarios, cinco categorías, tres temas y tres comentarios. Los usuarios admin, member y member2 tienen la clave de demostración `DemoPass123!`; se persisten hashes BCrypt. Cambia las cuentas/contraseñas antes de exponer la instancia. El servicio de usuarios aplica `DDL_AUTO=validate`: la definición SQL debe preceder a su arranque. Los IDs de autor del foro son referencias numéricas a usuarios; no hay escrituras cruzadas de usuarios.

### Volumen existente con inicialización incompleta

Los hooks de `database/container-init` y el SQL de `database/init` se ejecutan únicamente al crear un volumen Oracle vacío. Reiniciar contenedores o cambiar estos archivos no repara un volumen ya inicializado. **No borres ni recrees el volumen como reparación.**

Antes de una reparación manual no destructiva, el responsable debe:

1. Conservar el volumen existente y obtener una copia de seguridad recuperable; detener los servicios de aplicación para evitar escrituras durante la intervención.
2. Conectarse explícitamente a `FREEPDB1` con una cuenta administrativa autorizada, confirmar contenedor actual y revisar propietarios/estado de las cuatro tablas (`APP_USERS`, `FORUM_CATEGORY`, `FORUM_TOPIC`, `FORUM_COMMENT`) y sus datos. No modificar ni borrar objetos propiedad de `SYS` en `CDB$ROOT`.
3. Confirmar el usuario real de `ORACLE_APP_USER` y que exista en `FREEPDB1`, y validar privilegios/propiedad antes de crear objetos. No exponer credenciales en consola compartida, logs o historial.
4. Comparar el estado de cada tabla y datos seed con `database/init/01-forum-schema-and-seed.sql`. El archivo contiene `CREATE TABLE` e inserts no idempotentes: **no ejecutarlo completo sobre tablas/datos existentes**. Preparar una aplicación revisada que cree solo objetos ausentes en el esquema correcto y agregue únicamente filas faltantes, preservando IDs, relaciones y datos existentes.
5. Verificar después, todavía en `FREEPDB1` y bajo el propietario esperado, que las cuatro tablas existen, que los servicios pueden validar el esquema y que las filas existentes se conservaron. No se incluye aquí un comando de reparación automática; la intervención del volumen actual requiere inspección y aprobación explícita del responsable.

No conectar a, inspeccionar, reparar, reiniciar ni borrar un volumen como parte de la inicialización futura descrita arriba.

## Validar API con Postman

Importa `postman/Foro API.postman_collection.json` en Postman y ejecuta las solicitudes. Las direcciones, puertos, credenciales de demostración e ID de tema están escritos directamente en cada solicitud: no hace falta configurar un entorno ni variables de Postman. Cada solicitud protegida inicia sesión automáticamente antes de enviarse para obtener un token vigente. La colección verifica login/identidad, prohibición de gestión para USER, gestión admin, categorías, búsqueda/lectura de temas y publicación/lectura de comentarios. Si cambias los puertos predeterminados, actualiza las direcciones en las solicitudes y sus scripts de autenticación.

Rutas principales: `POST /api/auth/login`, `GET /api/auth/me`, administración `/api/users` (ADMIN); `/api/categories` (lectura autenticada, mutaciones ADMIN); `/api/topics` (búsqueda con `?search=`, CRUD autenticado con reglas de autor/ADMIN); comentarios en `/api/topics/{id}/comments`. El servicio de foro valida el token preguntando al servicio de usuarios; las llamadas al foro deben conservar el bearer token.

Moderación: `DELETE /moderation-api/topics/{topicId}` y `DELETE /moderation-api/topics/{topicId}/comments/{commentId}`. Se requiere `Authorization: Bearer <token>`; moderation verifica el token con `GET /api/auth/me` del servicio de usuarios y solo delega si la identidad devuelta tiene rol `ADMIN`. Reenvía el mismo bearer token al endpoint DELETE correspondiente del foro, que mantiene su propia autorización. El contrato actual del foro elimina temas/comentarios, por lo que estas acciones no son suspensiones reversibles.

## Guion de demostración (7–10 minutos)

1. **0:00–1:00 — propósito y arquitectura.** Explicar los servicios separados, Oracle compartido y validación de identidad entre ellos.
2. **1:00–2:30 — arranque.** Mostrar `.env` local sin publicar credenciales, `docker compose up --build -d`, los tres estados `healthy` y la aplicación en `http://localhost:4200`.
3. **2:30–4:00 — roles y acceso.** En Postman iniciar sesión como member; mostrar consulta autenticada y un `403` al intentar crear usuario/categoría. Iniciar como admin y mostrar acceso administrativo.
4. **4:00–6:00 — foro.** Listar categorías y temas, buscar un tema, publicar comentario y leerlo; explicar propiedad de autor y rol ADMIN.
5. **6:00–7:30 — persistencia.** Mostrar tablas/filas seed y explicar BCrypt y volumen Oracle nombrado; reiniciar servicios sin borrar el volumen y verificar que los datos persisten.
6. **7:30–9:00 — cierre.** Resumir rutas, controles de autorización, límites de demo local y trabajo futuro. Ajustar ritmo para terminar entre 7 y 10 minutos.

No se incluye comando de limpieza destructiva ni se debe eliminar el volumen como parte de la demostración.

## Arquetipo Maven backend

`archetype/` contiene un arquetipo Maven independiente que genera un esqueleto Spring Boot Java 17. Consulta [`archetype/README.md`](archetype/README.md) para instalarlo localmente y generar un proyecto. El arquetipo es una plantilla para nuevos proyectos; no hay evidencia de que los servicios `users-service`, `forum-service` o `moderation-service` existentes se hayan generado con él.
