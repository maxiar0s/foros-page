# Foro: servicios de usuarios y foro

Arranca Oracle Database Free y los dos servicios REST con Docker Compose. El montaje es una integración local para demostración: las credenciales de `.env.example` y cuentas seed son públicas de ejemplo, no secretos de producción.

## Inicio rápido

Requisitos: Docker Engine/Desktop con Compose v2. Clona/descarga el repositorio y, desde su raíz:

```sh
cp .env.example .env
# Edita .env para configurar contraseñas locales y un JWT_SECRET base64 propio.
docker compose config -q
docker compose up --build -d
docker compose ps
```

Espera a que `oracle`, `users-service` y `forum-service` indiquen `healthy`. Primera inicialización de Oracle puede tardar varios minutos. URLs: users `http://localhost:8081`, forum `http://localhost:8082`, Oracle `localhost:1521/FREEPDB1`. El volumen nombrado `foro-oracle-data` conserva los datos; los scripts de `database/init` solo se ejecutan al inicializar por primera vez un volumen vacío. No elimines volúmenes para reiniciar servicios; `docker compose down` detiene y retira contenedores, pero conserva el volumen. Para borrar datos se requiere una acción deliberada fuera del procedimiento normal.

El contenedor crea un usuario de aplicación de Oracle a partir de `ORACLE_APP_USER` y `ORACLE_APP_PASSWORD`; ambos servicios usan esa cuenta y su propio conjunto de tablas. `ORACLE_SYSTEM_PASSWORD`, credenciales de la aplicación y `JWT_SECRET` son editables localmente en `.env`. No subir `.env`, ni usar valores de ejemplo en producción. Usa valores aleatorios robustos, gestión de secretos y TLS en entornos no locales.

## Datos de demostración

En un volumen recién creado, el SQL crea `APP_USERS`, `FORUM_CATEGORY`, `FORUM_TOPIC` y `FORUM_COMMENT`, con tres usuarios, cinco categorías, tres temas y tres comentarios. Los usuarios admin, member y member2 tienen la clave de demostración `DemoPass123!`; se persisten hashes BCrypt. Cambia las cuentas/contraseñas antes de exponer la instancia. El servicio de usuarios aplica `DDL_AUTO=validate`: la definición SQL debe preceder a su arranque. Los IDs de autor del foro son referencias numéricas a usuarios; no hay escrituras cruzadas de usuarios.

## Validar API con Postman

Importa `postman/Foro API.postman_collection.json` en Postman y ejecuta la colección en orden. La colección guarda tokens de admin/usuario y verifica login/identidad, prohibición de gestión para USER, gestión admin, categorías, búsqueda/lectura de temas y publicación/lectura de comentarios. `baseUsers` y `baseForum` están definidos como variables de colección para permitir cambiar puertos.

Rutas principales: `POST /api/auth/login`, `GET /api/auth/me`, administración `/api/users` (ADMIN); `/api/categories` (lectura autenticada, mutaciones ADMIN); `/api/topics` (búsqueda con `?search=`, CRUD autenticado con reglas de autor/ADMIN); comentarios en `/api/topics/{id}/comments`. El servicio de foro valida el token preguntando al servicio de usuarios; las llamadas al foro deben conservar el bearer token.

## Guion de demostración (7–10 minutos)

1. **0:00–1:00 — propósito y arquitectura.** Explicar los servicios separados, Oracle compartido y validación de identidad entre ellos.
2. **1:00–2:30 — arranque.** Mostrar `.env` local sin publicar credenciales, `docker compose up --build -d` y los tres estados `healthy`.
3. **2:30–4:00 — roles y acceso.** En Postman iniciar sesión como member; mostrar consulta autenticada y un `403` al intentar crear usuario/categoría. Iniciar como admin y mostrar acceso administrativo.
4. **4:00–6:00 — foro.** Listar categorías y temas, buscar un tema, publicar comentario y leerlo; explicar propiedad de autor y rol ADMIN.
5. **6:00–7:30 — persistencia.** Mostrar tablas/filas seed y explicar BCrypt y volumen Oracle nombrado; reiniciar servicios sin borrar el volumen y verificar que los datos persisten.
6. **7:30–9:00 — cierre.** Resumir rutas, controles de autorización, límites de demo local y trabajo futuro. Ajustar ritmo para terminar entre 7 y 10 minutos.

No se incluye comando de limpieza destructiva ni se debe eliminar el volumen como parte de la demostración.
