export interface PasswordRuleResult { uppercase: boolean; lowercase: boolean; digit: boolean; special: boolean; length: boolean; }

export function passwordRules(value: string): PasswordRuleResult {
  return {
    uppercase: /[A-Z]/.test(value),
    lowercase: /[a-z]/.test(value),
    digit: /[0-9]/.test(value),
    special: /[^A-Za-z0-9]/.test(value),
    length: value.length >= 8 && value.length <= 72
  };
}
