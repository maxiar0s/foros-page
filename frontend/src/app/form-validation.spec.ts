import { isNonBlank } from './form-validation';

describe('isNonBlank', () => {
  it('rejects empty and whitespace-only required fields', () => {
    expect(isNonBlank('')).toBeFalse();
    expect(isNonBlank('   ')).toBeFalse();
    expect(isNonBlank('\n\t')).toBeFalse();
  });

  it('accepts text containing a non-whitespace character', () => {
    expect(isNonBlank(' tema ')).toBeTrue();
  });
});
