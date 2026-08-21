type GuanSeqLogoProps = {
  className?: string;
};

export function GuanSeqLogo({ className }: GuanSeqLogoProps) {
  return (
    <svg className={className} viewBox="0 0 40 40" role="img" aria-label="GuanSeq logo mark">
      <path d="M10 10h17.5a3.5 3.5 0 0 1 0 7H15a5 5 0 0 0 0 10h15" fill="none" stroke="currentColor" strokeWidth="3.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 10v8.5M30 21.5V30" fill="none" stroke="currentColor" strokeWidth="3.8" strokeLinecap="round" />
      <circle cx="10" cy="10" r="3" fill="currentColor" />
      <circle cx="30" cy="30" r="3" fill="currentColor" />
      <circle cx="20" cy="22" r="3.4" fill="#e7762f" />
    </svg>
  );
}
