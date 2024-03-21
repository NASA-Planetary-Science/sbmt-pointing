# sbmt-pointing

sbmt-pointing is a low level library for the overall [SBMT family of libraries](https://github.com/orgs/NASA-Planetary-Science/teams/sbmt/repositories). It contains classes that help translate pointing information (for example from Picante) into data that can be used by SBMT.


## Usage

sbmt-pointing is intended as a dependency for other libraries in the SBMT family.  You can either clone this library by itself, or use the [Eclipse project team set file](https://github.com/orgs/NASA-Planetary-Science/teams/sbmt/repositories/sbmt-client/teamProjectSet.psf) located in the [sbmt-client](https://github.com/orgs/NASA-Planetary-Science/teams/sbmt/repositories/sbmt-client) to pull down the entire family of libraries into an Eclipse workspace.

sbmt-core is available as a jar at [Maven Central](https://central.sonatype.com/artifact/edu.jhuapl.ses/sbmt-pointing).  The dependency listing is:

```
<dependency>
    <groupId>edu.jhuapl.ses.sbmt</groupId>
    <artifactId>sbmt-pointing</artifactId>
    <version>1.0.0</version>
</dependency>
```


## Contributing

Please see the [Contributing](Contributing.md) file for information. Pull requests will be reviewed and merged on a best-effort basis; there are no guarantees, due to funding restrictions.

## Code of Conduct

The SBMT family of libraries all support the [Github Code of Conduct](https://docs.github.com/en/site-policy/github-terms/github-community-code-of-conduct).

