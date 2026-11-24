import { passwordRules } from './password-rules';

describe('passwordRules', () => {
  it('requires uppercase, lowercase, digit, and special character', () => {
    expect(passwordRules('Abcdef1!')).toEqual({ uppercase: true, lowercase: true, digit: true, special: true, length: true });
    expect(passwordRules('abcdef1!')).toEqual({ uppercase: false, lowercase: true, digit: true, special: true, length: true });
    expect(passwordRules('ABCDEF1!')).toEqual({ uppercase: true, lowercase: false, digit: true, special: true, length: true });
    expect(passwordRules('Abcdefg!')).toEqual({ uppercase: true, lowercase: true, digit: false, special: true, length: true });
    expect(passwordRules('Abcdef12')).toEqual({ uppercase: true, lowercase: true, digit: true, special: false, length: true });
  });

  it('enforces the API password length boundaries', () => {
    expect(passwordRules('Ab1!')).toEqual({ uppercase: true, lowercase: true, digit: true, special: true, length: false });
    expect(passwordRules('A' + 'b'.repeat(69) + '1!').length).toBeTrue();
    expect(passwordRules('A' + 'b'.repeat(70) + '1!').length).toBeFalse();
  });
});
