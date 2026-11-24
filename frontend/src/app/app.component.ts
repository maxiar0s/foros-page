import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { passwordRules } from './password-rules';
import { isNonBlank } from './form-validation';

interface Identity { id: number; username: string; role: 'USER' | 'ADMIN'; }
interface Profile extends Identity { email: string; }
interface Category { id: number; name: string; }
interface Topic { id: number; title: string; body: string; authorId: number; category: Category; createdAt: string; }
interface Comment { id: number; body: string; authorId: number; createdAt: string; }
interface AdminUser { id: number; username: string; email: string; role: 'USER' | 'ADMIN'; }
type Screen = 'login' | 'register' | 'forum' | 'profile' | 'recovery' | 'reset';

@Component({
  selector: 'app-root', standalone: true, imports: [CommonModule, FormsModule],
  template: `
    <header class="border-bottom bg-white"><nav class="container navbar navbar-expand-md" aria-label="Navegación principal">
      <a class="navbar-brand fw-bold" href="#" (click)="$event.preventDefault(); go('forum')">Foro DUOC</a>
      <div class="ms-auto d-flex gap-2 align-items-center">
        <ng-container *ngIf="identity; else guestNav">
          <span class="small text-secondary">{{identity.username}} · {{identity.role}}</span>
          <button class="btn btn-outline-primary btn-sm" (click)="go('profile')">Mi perfil</button>
          <button class="btn btn-outline-secondary btn-sm" (click)="logout()">Salir</button>
        </ng-container>
        <ng-template #guestNav>
          <button class="btn btn-link" (click)="go('login')">Iniciar sesión</button>
          <button class="btn btn-primary btn-sm" (click)="go('register')">Crear cuenta</button>
        </ng-template>
      </div>
    </nav></header>
    <main class="container py-4">
      <div *ngIf="notice" class="alert" [class.alert-danger]="error" [class.alert-success]="!error" role="status">{{notice}}</div>
      <section *ngIf="screen === 'login'" class="row justify-content-center"><div class="col-12 col-md-7 col-lg-5">
        <h1 class="h3 mb-3">Iniciar sesión</h1><form (ngSubmit)="login()" #loginForm="ngForm" novalidate>
          <label class="form-label" for="login-username">Nombre de usuario</label><input id="login-username" name="username" class="form-control mb-3" [(ngModel)]="loginData.username" required autocomplete="username">
          <label class="form-label" for="login-password">Contraseña</label><input id="login-password" name="password" type="password" class="form-control mb-3" [(ngModel)]="loginData.password" required autocomplete="current-password">
          <button class="btn btn-primary w-100" [disabled]="loginForm.invalid">Entrar</button>
        </form><button class="btn btn-link px-0 mt-2" (click)="showRecovery()">¿Olvidaste tu contraseña?</button>
        <p>¿No tienes cuenta? <button class="btn btn-link px-0" (click)="go('register')">Regístrate</button></p>
      </div></section>
      <section *ngIf="screen === 'register'" class="row justify-content-center"><div class="col-12 col-md-8 col-lg-6">
        <h1 class="h3 mb-3">Crear cuenta</h1><form (ngSubmit)="register()" #registerForm="ngForm" novalidate>
          <label class="form-label" for="reg-username">Nombre de usuario</label><input id="reg-username" name="username" class="form-control mb-3" [(ngModel)]="registerData.username" required maxlength="50" autocomplete="username">
          <label class="form-label" for="reg-email">Correo electrónico</label><input id="reg-email" name="email" type="email" class="form-control mb-3" [(ngModel)]="registerData.email" required email maxlength="254" autocomplete="email">
          <label class="form-label" for="reg-password">Contraseña</label><input id="reg-password" name="password" type="password" class="form-control" [(ngModel)]="registerData.password" required minlength="8" maxlength="72" autocomplete="new-password" aria-describedby="password-rules">
          <ul id="password-rules" class="small mt-2" aria-label="Requisitos de contraseña"><li *ngFor="let rule of ruleList" [class.text-success]="passwordRules(registerData.password)[rule.key]" [class.text-danger]="!passwordRules(registerData.password)[rule.key]">{{rule.label}}</li></ul>
          <label class="form-label" for="reg-confirm">Confirmar contraseña</label><input id="reg-confirm" name="confirm" type="password" class="form-control mb-3" [(ngModel)]="registerData.confirm" required autocomplete="new-password">
          <button class="btn btn-primary w-100" [disabled]="registerForm.invalid || !passwordValid || registerData.password !== registerData.confirm">Crear cuenta</button>
        </form><p class="mt-3">¿Ya tienes cuenta? <button class="btn btn-link px-0" (click)="go('login')">Inicia sesión</button></p>
      </div></section>
      <section *ngIf="screen === 'recovery'" class="row justify-content-center"><div class="col-12 col-md-7 col-lg-5">
        <h1 class="h3 mb-3">Recuperar contraseña</h1><p class="text-secondary">Te enviaremos instrucciones si existe una cuenta con ese correo.</p>
        <form (ngSubmit)="requestRecovery()" #recoveryForm="ngForm"><label class="form-label" for="recovery-email">Correo electrónico</label><input id="recovery-email" name="email" type="email" class="form-control mb-3" [(ngModel)]="recoveryEmail" required email maxlength="254" autocomplete="email"><button class="btn btn-primary w-100" [disabled]="recoveryForm.invalid">Enviar instrucciones</button></form>
        <button class="btn btn-link px-0 mt-2" (click)="go('login')">Volver a iniciar sesión</button>
      </div></section>
      <section *ngIf="screen === 'reset'" class="row justify-content-center"><div class="col-12 col-md-7 col-lg-5">
        <h1 class="h3 mb-3">Elegir nueva contraseña</h1><form (ngSubmit)="resetPassword()" #resetForm="ngForm">
          <label class="form-label" for="reset-password">Nueva contraseña</label><input id="reset-password" name="password" type="password" class="form-control" [(ngModel)]="resetData.password" required minlength="8" maxlength="72" autocomplete="new-password" aria-describedby="reset-password-rules">
          <ul id="reset-password-rules" class="small mt-2" aria-label="Requisitos de contraseña"><li *ngFor="let rule of ruleList" [class.text-success]="passwordRules(resetData.password)[rule.key]" [class.text-danger]="!passwordRules(resetData.password)[rule.key]">{{rule.label}}</li></ul>
          <label class="form-label" for="reset-confirm">Confirmar contraseña</label><input id="reset-confirm" name="confirm" type="password" class="form-control mb-3" [(ngModel)]="resetData.confirm" required autocomplete="new-password">
          <button class="btn btn-primary w-100" [disabled]="resetForm.invalid || !passwordValidForReset || resetData.password !== resetData.confirm">Cambiar contraseña</button>
        </form><button class="btn btn-link px-0 mt-2" (click)="go('login')">Volver a iniciar sesión</button>
      </div></section>
      <section *ngIf="screen === 'profile'" class="row justify-content-center"><div class="col-12 col-md-8 col-lg-6">
        <h1 class="h3">Mi perfil</h1><p class="text-secondary">Actualiza tus datos de contacto. El nombre de usuario no se puede cambiar.</p>
        <form (ngSubmit)="saveProfile()" #profileForm="ngForm"><label class="form-label" for="profile-username">Nombre de usuario</label><input id="profile-username" class="form-control mb-3" [value]="profile.username" readonly aria-readonly="true">
          <label class="form-label" for="profile-email">Correo electrónico</label><input id="profile-email" name="email" type="email" class="form-control mb-3" [(ngModel)]="profile.email" required email maxlength="254">
          <button class="btn btn-primary" [disabled]="profileForm.invalid">Guardar cambios</button></form>
        <hr><h2 class="h5">Seguridad</h2><button class="btn btn-link px-0" (click)="showRecovery()">Enviar instrucciones para cambiar mi contraseña</button>
      </div></section>
      <section *ngIf="screen === 'forum'">
        <div class="row align-items-center mb-4"><div class="col-12 col-md-8"><h1 class="h3 mb-1">Conversaciones de la comunidad</h1><p class="text-secondary mb-0">Busca un tema o explora por categoría.</p></div>
          <div class="col-12 col-md-4 text-md-end" *ngIf="identity"><button class="btn btn-primary mt-3 mt-md-0" (click)="newTopic()">Nuevo tema</button></div></div>
        <div class="row g-3 mb-4"><div class="col-12 col-md-8"><label class="visually-hidden" for="search">Buscar temas</label><input id="search" class="form-control" [(ngModel)]="search" (ngModelChange)="loadTopics()" placeholder="Buscar temas..." aria-label="Buscar temas"></div>
          <div class="col-12 col-md-4"><label class="visually-hidden" for="category-filter">Filtrar por categoría</label><select id="category-filter" class="form-select" [(ngModel)]="categoryFilter"><option value="">Todas las categorías</option><option *ngFor="let category of categories" [value]="category.id">{{category.name}}</option></select></div></div>
        <div class="row g-4"><div class="col-12 col-lg-8"><article *ngFor="let topic of visibleTopics" class="card mb-3"><div class="card-body">
          <div class="d-flex justify-content-between gap-2"><div><span class="badge text-bg-light">{{topic.category.name}}</span><h2 class="h5 mt-2">{{topic.title}}</h2></div><button *ngIf="canModerate(topic.authorId)" class="btn btn-sm btn-outline-danger align-self-start" (click)="deleteTopic(topic)" aria-label="Eliminar tema">Eliminar</button></div>
          <p class="mb-2 text-break">{{topic.body}}</p><div class="small text-secondary">Por usuario {{topic.authorId}} · {{topic.createdAt | date:'mediumDate'}}</div>
          <button class="btn btn-link px-0" (click)="toggleComments(topic)">Comentarios ({{commentCounts[topic.id] || 0}})</button>
          <div *ngIf="openTopicId === topic.id" class="border-top pt-3"><div *ngFor="let comment of comments" class="mb-3"><p class="mb-1">{{comment.body}}</p><span class="small text-secondary">Usuario {{comment.authorId}}</span><button *ngIf="canModerate(comment.authorId)" class="btn btn-sm btn-link text-danger" (click)="deleteComment(topic, comment)">Eliminar</button></div>
            <form *ngIf="identity" (ngSubmit)="addComment(topic)" #commentForm="ngForm" class="d-flex gap-2"><label class="visually-hidden" [for]="'comment-'+topic.id">Escribe un comentario</label><input [id]="'comment-'+topic.id" name="comment" class="form-control" [(ngModel)]="commentDraft" required maxlength="2000"><button class="btn btn-outline-primary" [disabled]="commentForm.invalid || !isNonBlank(commentDraft)">Comentar</button></form>
            <p *ngIf="!identity" class="small">Inicia sesión para comentar.</p>
          </div>
        </div></article><p *ngIf="!visibleTopics.length" class="text-secondary">No hay temas para mostrar.</p></div>
        <aside class="col-12 col-lg-4"><div class="card"><div class="card-body"><h2 class="h5">Categorías</h2><ul class="list-unstyled mb-0"><li *ngFor="let category of categories" class="py-1">{{category.name}} <button class="btn btn-sm btn-link" (click)="categoryFilter = category.id.toString()">Ver</button><button *ngIf="identity?.role === 'ADMIN'" class="btn btn-sm btn-link text-danger" (click)="deleteCategory(category)" aria-label="Eliminar categoría">Eliminar</button></li></ul>
          <form *ngIf="identity?.role === 'ADMIN'" (ngSubmit)="addCategory()" class="mt-3"><label class="form-label" for="new-category">Agregar categoría</label><div class="input-group"><input id="new-category" name="category" class="form-control" [(ngModel)]="categoryDraft" required maxlength="100"><button class="btn btn-outline-primary">Agregar</button></div></form></div></div>
          <div *ngIf="identity?.role === 'ADMIN'" class="card mt-3"><div class="card-body"><h2 class="h5">Administrar usuarios</h2><button class="btn btn-sm btn-outline-primary" (click)="loadUsers()">Ver usuarios</button><ul *ngIf="users.length" class="list-unstyled mt-2"><li *ngFor="let user of users" class="py-1">{{user.username}} · {{user.role}}</li></ul></div></div>
        </aside></div>
      </section>
      <div *ngIf="screen === 'forum' && identity && topicFormOpen" class="modal d-block" role="dialog" aria-modal="true" aria-labelledby="topic-dialog-title"><div class="modal-dialog"><div class="modal-content"><form (ngSubmit)="createTopic()" #topicForm="ngForm"><div class="modal-header"><h2 id="topic-dialog-title" class="modal-title fs-5">Nuevo tema</h2><button type="button" class="btn-close" aria-label="Cerrar" (click)="topicFormOpen=false"></button></div><div class="modal-body"><label class="form-label" for="topic-title">Título</label><input id="topic-title" name="title" class="form-control mb-3" [(ngModel)]="topicDraft.title" required maxlength="200"><label class="form-label" for="topic-category">Categoría</label><select id="topic-category" name="category" class="form-select mb-3" [(ngModel)]="topicDraft.categoryId" required><option [ngValue]="null">Elige una categoría</option><option *ngFor="let c of categories" [ngValue]="c.id">{{c.name}}</option></select><label class="form-label" for="topic-body">Mensaje</label><textarea id="topic-body" name="body" class="form-control" rows="5" [(ngModel)]="topicDraft.body" required maxlength="4000"></textarea></div><div class="modal-footer"><button type="button" class="btn btn-secondary" (click)="topicFormOpen=false">Cancelar</button><button class="btn btn-primary" [disabled]="topicForm.invalid || !isNonBlank(topicDraft.title) || !isNonBlank(topicDraft.body)">Publicar</button></div></form></div></div></div>
    </main>
    <footer class="container py-4 border-top text-secondary small">Foro DUOC · Conversaciones para aprender en comunidad</footer>
  `
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);
  screen: Screen = 'login';
  identity: Identity | null = null;
  profile: Profile = { id: 0, username: '', email: '', role: 'USER' };
  loginData = { username: '', password: '' };
  registerData = { username: '', email: '', password: '', confirm: '' };
  recoveryEmail = '';
  resetData = { token: '', password: '', confirm: '' };
  categories: Category[] = [];
  topics: Topic[] = [];
  comments: Comment[] = [];
  users: AdminUser[] = [];
  commentCounts: Record<number, number> = {};
  openTopicId: number | null = null;
  search = '';
  categoryFilter = '';
  categoryDraft = '';
  commentDraft = '';
  topicDraft: { title: string; body: string; categoryId: number | null } = { title: '', body: '', categoryId: null };
  topicFormOpen = false;
  notice = '';
  error = false;
  readonly ruleList = [
    { key: 'uppercase' as const, label: 'Al menos una letra mayúscula' },
    { key: 'lowercase' as const, label: 'Al menos una letra minúscula' },
    { key: 'digit' as const, label: 'Al menos un número' },
    { key: 'special' as const, label: 'Al menos un carácter especial' },
    { key: 'length' as const, label: 'Entre 8 y 72 caracteres' }
  ];

  ngOnInit(): void {
    const resetToken = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('resetToken');
    if (resetToken) { this.resetData.token = resetToken; this.screen = 'reset'; }
    if (localStorage.getItem('foro-token')) {
      this.http.get<Identity>('/users-api/auth/me').subscribe({ next: user => { this.identity = user; this.screen = 'forum'; this.loadProfile(); this.loadForum(); }, error: () => this.logout(false) });
    }
  }

  get passwordValid(): boolean { return Object.values(passwordRules(this.registerData.password)).every(Boolean); }
  get passwordValidForReset(): boolean { return Object.values(passwordRules(this.resetData.password)).every(Boolean); }
  passwordRules(value: string) { return passwordRules(value); }
  isNonBlank(value: string): boolean { return isNonBlank(value); }
  get visibleTopics(): Topic[] { return this.topics.filter(topic => !this.categoryFilter || topic.category?.id.toString() === this.categoryFilter); }
  go(screen: Screen): void { this.screen = screen; this.notice = ''; this.error = false; if (screen === 'forum') this.loadForum(); }

  login(): void {
    this.http.post<{ token: string }>('/users-api/auth/login', this.loginData).subscribe({ next: response => { localStorage.setItem('foro-token', response.token); this.http.get<Identity>('/users-api/auth/me').subscribe({ next: user => { this.identity = user; this.screen = 'forum'; this.setNotice('¡Bienvenido al foro!'); this.loadProfile(); this.loadForum(); }, error: e => this.fail(e) }); }, error: e => this.fail(e) });
  }
  register(): void {
    const { username, email, password } = this.registerData;
    this.http.post('/users-api/auth/register', { username, email, password }).subscribe({ next: () => { this.registerData = { username: '', email: '', password: '', confirm: '' }; this.go('login'); this.setNotice('Cuenta creada. Ya puedes iniciar sesión.'); }, error: e => this.fail(e) });
  }
  logout(show = true): void { localStorage.removeItem('foro-token'); this.identity = null; this.screen = 'login'; if (show) this.setNotice('Sesión cerrada.'); }
  showRecovery(): void { this.recoveryEmail = this.profile.email || ''; this.go('recovery'); }
  requestRecovery(): void {
    this.http.post('/users-api/auth/recover', { email: this.recoveryEmail.trim() }).subscribe({
      next: () => { this.screen = 'login'; this.setNotice('Si existe una cuenta con ese correo, recibirás instrucciones para recuperar la contraseña.'); },
      error: e => this.fail(e)
    });
  }
  resetPassword(): void {
    if (!this.resetData.token || !this.passwordValidForReset || this.resetData.password !== this.resetData.confirm) return;
    this.http.post('/users-api/auth/reset-password', { token: this.resetData.token, password: this.resetData.password }).subscribe({
      next: () => { this.resetData = { token: '', password: '', confirm: '' }; this.screen = 'login'; this.setNotice('Contraseña actualizada. Ya puedes iniciar sesión.'); },
      error: e => this.fail(e)
    });
  }
  private loadProfile(): void { this.http.get<Profile>('/users-api/users/me').subscribe({ next: p => this.profile = p, error: e => this.fail(e) }); }
  saveProfile(): void { this.http.put<Profile>('/users-api/users/me', { username: this.profile.username, email: this.profile.email }).subscribe({ next: p => { this.profile = p; this.setNotice('Perfil actualizado.'); }, error: e => this.fail(e) }); }
  private loadForum(): void {
    this.http.get<Category[]>('/forum-api/categories').subscribe({ next: data => this.categories = data, error: e => this.fail(e) });
    this.loadTopics();
  }
  loadTopics(): void {
    let params = new HttpParams(); if (this.search.trim()) params = params.set('search', this.search.trim());
    this.http.get<Topic[]>('/forum-api/topics', { params }).subscribe({ next: topics => { this.topics = topics; topics.forEach(t => this.http.get<Comment[]>(`/forum-api/topics/${t.id}/comments`).subscribe({ next: comments => this.commentCounts[t.id] = comments.length })); }, error: e => this.fail(e) });
  }
  toggleComments(topic: Topic): void { this.openTopicId = this.openTopicId === topic.id ? null : topic.id; if (this.openTopicId) this.http.get<Comment[]>(`/forum-api/topics/${topic.id}/comments`).subscribe({ next: data => this.comments = data, error: e => this.fail(e) }); }
  addComment(topic: Topic): void { const body = this.commentDraft.trim(); if (!body) return; this.http.post<Comment>(`/forum-api/topics/${topic.id}/comments`, { body }).subscribe({ next: () => { this.commentDraft = ''; this.toggleComments(topic); this.toggleComments(topic); this.commentCounts[topic.id] = (this.commentCounts[topic.id] || 0) + 1; }, error: e => this.fail(e) }); }
  newTopic(): void { this.topicDraft = { title: '', body: '', categoryId: null }; this.topicFormOpen = true; }
  createTopic(): void {
    const title = this.topicDraft.title.trim();
    const body = this.topicDraft.body.trim();
    if (!title || !body || !this.topicDraft.categoryId) return;
    this.http.post('/forum-api/topics', { ...this.topicDraft, title, body }).subscribe({ next: () => { this.topicFormOpen = false; this.setNotice('Tema publicado.'); this.loadTopics(); }, error: e => this.fail(e) });
  }
  canModerate(authorId: number): boolean { return this.identity?.role === 'ADMIN' || this.identity?.id === authorId; }
  deleteTopic(topic: Topic): void { if (!confirm(`¿Eliminar “${topic.title}”?`)) return; const url = this.identity?.role === 'ADMIN' ? `/moderation-api/topics/${topic.id}` : `/forum-api/topics/${topic.id}`; this.http.delete(url).subscribe({ next: () => { this.topics = this.topics.filter(t => t.id !== topic.id); this.setNotice('Tema eliminado.'); }, error: e => this.fail(e) }); }
  deleteComment(topic: Topic, comment: Comment): void { if (!confirm('¿Eliminar este comentario?')) return; const url = this.identity?.role === 'ADMIN' ? `/moderation-api/topics/${topic.id}/comments/${comment.id}` : `/forum-api/topics/${topic.id}/comments/${comment.id}`; this.http.delete(url).subscribe({ next: () => { this.comments = this.comments.filter(c => c.id !== comment.id); this.commentCounts[topic.id]--; }, error: e => this.fail(e) }); }
  addCategory(): void { const name = this.categoryDraft.trim(); if (!name) return; this.http.post('/forum-api/categories', { name }).subscribe({ next: () => { this.categoryDraft = ''; this.loadForum(); }, error: e => this.fail(e) }); }
  deleteCategory(category: Category): void { if (!confirm(`¿Eliminar la categoría ${category.name}?`)) return; this.http.delete(`/forum-api/categories/${category.id}`).subscribe({ next: () => this.loadForum(), error: e => this.fail(e) }); }
  loadUsers(): void { this.http.get<AdminUser[]>('/users-api/users').subscribe({ next: users => this.users = users, error: e => this.fail(e) }); }
  private setNotice(message: string): void { this.notice = message; this.error = false; }
  private fail(error: HttpErrorResponse): void { this.notice = error.status === 0 ? 'No se pudo conectar con el servicio. Inténtalo más tarde.' : error.error?.detail || error.error?.message || 'No se pudo completar la solicitud.'; this.error = true; }
}
