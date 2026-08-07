// Fibonacci — bottom-up DP (tabulation)
function fiboUp(n) {
  if (n <= 1) return n;
  var dp = new Array(n + 1);
  dp[0] = 0;
  dp[1] = 1;
  for (var i = 2; i <= n; i++) {
    dp[i] = dp[i - 1] + dp[i - 2];
  }
  return dp[n];
}

// Fibonacci — top-down DP (memoization)
var memo = {};
function fiboDown(n) {
  if (n <= 1) return n;
  if (memo[n] !== undefined) return memo[n];
  memo[n] = fiboDown(n - 1) + fiboDown(n - 2);
  return memo[n];
}

var n = 10;
print("Fibonacci DP Demo (n = " + n + ")");
print("Bottom-up : " + fiboUp(n));
print("Top-down  : " + fiboDown(n));

print("Sequence (0.." + n + "):");
var seq = [];
for (var i = 0; i <= n; i++) {
  seq.push(fiboUp(i));
}
print(seq.join(" "));
