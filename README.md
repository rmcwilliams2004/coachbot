# coachbot

Instant customizable motivation.

[![Build Status](https://travis-ci.org/couragelabs/coachbot.svg?branch=master)]
(https://travis-ci.org/couragelabs/coachbot)

## Usage

### Run the application locally

`lein ring server`

### Run the tests

`lein spec`

### Packaging and running as standalone jar

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging and running as standalone jar like Heroku does
Install the [Heroku Toolbelt](https://toolbelt.heroku.com/), then:

```
lein do clean, ring uberjar 
heroku local web
```

### Deploying to Heroku

After having added the heroku remote:

    heroku git:remote -r production -a cl-coachbot
    
Then:
    
    git push production master

### Packaging as war

`lein ring uberwar`

## License

    Copyright © 2016, Courage Labs, LLC.
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
