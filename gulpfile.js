var gulp = require('gulp');
var coffee = require('gulp-coffee');
var gutil = require('gulp-util');

var paths = {
  coffee: ['src/main/js/*.coffee']
};


gulp.task('coffee', function() {
  gulp.src(paths.coffee)
    .pipe(coffee({bare: true}).on('error', gutil.log))
    .pipe(gulp.dest('./src/main/js'))
});

gulp.task('watch', function() {
  gulp.watch(paths.coffee, ['coffee']);
});

gulp.task('default', ['coffee', 'watch']);
