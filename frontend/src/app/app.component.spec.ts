import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';

describe('AppComponent validation and startup', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    localStorage.removeItem('foro-token');
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('does not request authenticated forum data when there is no saved token', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    http.expectNone('/forum-api/categories');
    http.expectNone('/forum-api/topics');
    fixture.destroy();
  });

  it('disables publishing and sends no request for whitespace-only topic fields', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.identity = { id: 1, username: 'member', role: 'USER' };
    app.screen = 'forum';
    app.topicFormOpen = true;
    app.topicDraft = { title: '   ', body: '\n\t', categoryId: 1 };
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    const publish = Array.from(buttons).find(button => button.textContent?.trim() === 'Publicar');
    if (!publish) throw new Error('No se encontró el botón Publicar');
    expect(publish.disabled).toBeTrue();
    app.createTopic();
    http.expectNone('/forum-api/topics');
    fixture.destroy();
  });

  it('requests password recovery without exposing whether the email is registered', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.screen = 'recovery';
    app.recoveryEmail = 'member@example.test';
    app.requestRecovery();
    const request = http.expectOne('/users-api/auth/recover');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'member@example.test' });
    request.flush({ message: 'If the account exists, recovery instructions will be sent.' }, { status: 202, statusText: 'Accepted' });
    expect(app.screen).toBe('login');
    expect(app.notice).toContain('Si existe una cuenta');
    fixture.destroy();
  });

  it('submits the reset token and validated new password without storing authentication', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.screen = 'reset';
    app.resetData = { token: 'opaque-reset-token', password: 'Strong-pass-123!', confirm: 'Strong-pass-123!' };
    app.resetPassword();
    const request = http.expectOne('/users-api/auth/reset-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'opaque-reset-token', password: 'Strong-pass-123!' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(app.screen).toBe('login');
    expect(localStorage.getItem('foro-token')).toBeNull();
    fixture.destroy();
  });

  it('routes administrator topic removal through the moderation service', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.identity = { id: 1, username: 'admin', role: 'ADMIN' };
    const topic = { id: 12, title: 'Tema moderado', body: 'Contenido', authorId: 2, category: { id: 1, name: 'Ayuda' }, createdAt: '' };
    spyOn(window, 'confirm').and.returnValue(true);

    app.deleteTopic(topic);

    const request = http.expectOne('/moderation-api/topics/12');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(app.topics).toEqual([]);
    fixture.destroy();
  });

  it('keeps owner topic removal on the forum service', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.identity = { id: 2, username: 'member', role: 'USER' };
    const topic = { id: 12, title: 'Mi tema', body: 'Contenido', authorId: 2, category: { id: 1, name: 'Ayuda' }, createdAt: '' };
    app.topics = [topic];
    spyOn(window, 'confirm').and.returnValue(true);

    app.deleteTopic(topic);

    const request = http.expectOne('/forum-api/topics/12');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(app.topics).toEqual([]);
    fixture.destroy();
  });

  it('routes administrator comment removal through moderation and owner removal through forum', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const topic = { id: 12, title: 'Tema', body: 'Contenido', authorId: 2, category: { id: 1, name: 'Ayuda' }, createdAt: '' };
    const comment = { id: 34, body: 'Comentario', authorId: 2, createdAt: '' };
    spyOn(window, 'confirm').and.returnValue(true);

    app.identity = { id: 1, username: 'admin', role: 'ADMIN' };
    app.comments = [comment];
    app.commentCounts[topic.id] = 1;
    app.deleteComment(topic, comment);
    const adminRequest = http.expectOne('/moderation-api/topics/12/comments/34');
    expect(adminRequest.request.method).toBe('DELETE');
    adminRequest.flush(null, { status: 204, statusText: 'No Content' });

    app.identity = { id: 2, username: 'member', role: 'USER' };
    app.comments = [comment];
    app.commentCounts[topic.id] = 1;
    app.deleteComment(topic, comment);
    const ownerRequest = http.expectOne('/forum-api/topics/12/comments/34');
    expect(ownerRequest.request.method).toBe('DELETE');
    ownerRequest.flush(null, { status: 204, statusText: 'No Content' });
    expect(app.comments).toEqual([]);
    expect(app.commentCounts[topic.id]).toBe(0);
    fixture.destroy();
  });

  it('disables commenting for whitespace-only content and sends no request', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.identity = { id: 1, username: 'member', role: 'USER' };
    app.screen = 'forum';
    app.topics = [{ id: 1, title: 'Tema', body: 'Contenido', authorId: 2, category: { id: 1, name: 'Ayuda' }, createdAt: '' }];
    app.openTopicId = 1;
    app.commentDraft = '   ';
    fixture.detectChanges();

    const commentInput = fixture.nativeElement.querySelector('#comment-1') as HTMLInputElement;
    const commentButton = commentInput.closest('form')?.querySelector('button') as HTMLButtonElement;
    expect(commentButton.disabled).toBeTrue();
    app.addComment(app.topics[0]);
    http.expectNone('/forum-api/topics/1/comments');
    fixture.destroy();
  });
});
